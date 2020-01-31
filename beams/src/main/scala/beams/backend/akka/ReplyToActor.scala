package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import zio._

import scala.util.control.NonFatal

private[akka] object ReplyToActor {
  type Ref[A] = ActorRef[Command[A]]

  sealed trait Command[+A]

  /**
   * Register task actor reference. This message is sent once by the executor actor.
   */
  final case class Register[A](ref: TaskActor.Ref[A]) extends Command[A] with SerializableMessage

  /**
   * Task completed. This message is sent once by the task actor.
   */
  final case class Done[+A](exit: Exit[Throwable, A]) extends Command[A] with SerializableMessage

  /**
   * Interrupt current task. This message is sent by the waiting ZIO task.
   * Callback will not be invoked if Interrupt message sent.
   */
  object Interrupt extends Command[Nothing] with NonSerializableMessage

  /**
   * Task actor terminated (possibly abnormally).
   * An error will be propagated to waiting ZIO task.
   */
  object TaskTerminated extends Command[Nothing] with SerializableMessage

  /**
   * Excecutor actor terminated (possibly abnormally).
   * An error will be propagated to waiting ZIO task if task not yet completed.
   */
  object ExecutorTerminated extends Command[Nothing] with SerializableMessage

  //TODO: тут можно поставить try-catch защиту с перенаправлением ошибок в cb
  def apply[A](executor: NodeActor.Ref[Any], cb: Task[A] => Unit): Behavior[Command[A]] = Behaviors.setup { ctx =>
    try {
      ctx.watchWith(executor, ExecutorTerminated)

      // Behaviors designed to process in-order and out-of-order messages and not to put too much messages to the dead letter queue.
      Behaviors.receiveMessagePartial {
        case Register(task) => // Normal message order (register -> done).
          ctx.unwatch(executor) // Unwatch executor since wa already have task actor reference to work with.
          ctx.watchWith(task, TaskTerminated) // Watch task actor reference.

          Behaviors.receiveMessagePartial {
            case Done(exit) => // Task completed. Unwatch and stop.
              cb(Task.done(exit))
              ctx.unwatch(task)
              Behaviors.stopped
            case Interrupt => // Interrupt message received. Cancel task, unwatch and stop.
              task ! TaskActor.Interrupt
              ctx.unwatch(task)
              Behaviors.stopped
            case TaskTerminated => // Task actor terminated before completion. Propagate error and stop.
              cb(Task.fail(ActorTerminatedException("Task actor terminated.", task)))
              Behaviors.stopped
          }

        case Done(exit) => // Reverse message order (done -> register).
          Behaviors.receiveMessagePartial {
            case Register(_) => // Register message received. Propagate result.
              cb(Task.done(exit))
              ctx.unwatch(executor)
              Behaviors.stopped
            case Interrupt => // Ignore interrupt message since task already been completed unwatch and stop. Do not propagate result.
              ctx.unwatch(executor)
              Behaviors.stopped
            case ExecutorTerminated => // Executor terminated, but task already been completed, it's OK, propagate result and stop.
              cb(Task.done(exit))
              Behaviors.stopped
          }

        case Interrupt => // Interrupt before register. We will attemp to wait register message to cancel task.
          Behaviors.receiveMessagePartial {
            case Register(task) => // Task registered. Send interrupt message unwatch and stop.
              task ! TaskActor.Interrupt
              ctx.unwatch(executor)
              Behaviors.stopped
            case Done(_) => // Task completed (before registration). Ignore task result, and further wait for the register message.
              Behaviors.receiveMessagePartial {
                case Register(_) => // Register message received. Unwatch and stop.
                  ctx.unwatch(executor)
                  Behaviors.stopped
                case ExecutorTerminated => // Executor terminated. But task compeled and ZIO interrupted. It's ok just stop.
                  Behaviors.stopped
              }
            case ExecutorTerminated => // Executor terminated and ZIO task interrupted. We will not receive task reference and likely will not receive Done message, just stop.
              Behaviors.stopped
          }

        case ExecutorTerminated => // Executor terminated before receiving task actor reference. Executor actor likely terminated, propagate error and stop.
          cb(Task.fail(ActorTerminatedException("Executor actor terminated.", executor)))
          Behaviors.stopped
      }
    } catch {
      case NonFatal(e) =>
        cb(Task.fail(e))
        Behaviors.stopped
    }
  }
}
