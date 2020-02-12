package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams.backend.akka.TaskExecutor._
import zio._

private abstract class TaskExecutor[R, A](runtime: Runtime[R], task: RIO[R, A], deathWatches: Seq[ActorRef[Nothing]]) {
  private def interrupting: Behavior[Command[A]] = Behaviors.receiveMessagePartial {
    case Done(_) => Behaviors.stopped // shutdown after the task interruption
    case _: Command[_] => Behaviors.same // swallow all other messages
  }

  protected final def actorTerminatedException(actor: ActorRef[Nothing]): ActorTerminatedException =
    ActorTerminatedException("Essential actor terminated before completing the task.", actor)

  protected final val behaviour: Behavior[Command[A]] = Behaviors.setup { ctx =>
    guardBehavior1(error) {
      deathWatches.foreach { actor => ctx.watchWith(actor, ActorTerminated(actor)) }

      val taskFiber = runtime.unsafeRun(task.fork) // для локальной задачи не нужен механизм прерывания.
      runtime.unsafeRunAsync(taskFiber.join)(ctx.self ! Done(_))

      Behaviors.receiveMessagePartial {
        case ActorTerminated(actor) => guardBehavior1(error) {
          runtime.unsafeRun(taskFiber.interrupt)
          terminated(actor)
          deathWatches.foreach(ctx.unwatch)
          interrupting
        }
        case Done(exit) => guardBehavior1(error) {
          done(exit)
          deathWatches.foreach(ctx.unwatch)
          Behaviors.stopped
        }
        case Interrupt => guardBehavior1(error) {
          runtime.unsafeRun(taskFiber.interrupt)
          deathWatches.foreach(ctx.unwatch)
          interrupting
        }
      }
    }
  }

  protected def done(r: Exit[Throwable, A])

  protected def error(e: Throwable): Unit

  protected def terminated(actor: ActorRef[Nothing])
}

private object TaskExecutor {
  type Ref[A] = ActorRef[Command[A]]

  sealed trait Command[+A]

  /**
    * Essential actor terminated.
    */
  private final case class ActorTerminated(ref: ActorRef[Nothing]) extends Command[Nothing]

  /**
    * Task completed. Sent by the actor to itself.
    */
  private final case class Done[A](exit: Exit[Throwable, A]) extends Command[A] with NonSerializableMessage

  /**
    * Interrupt the task. Send by waiting part.
    */
  object Interrupt extends Command[Nothing] with SerializableMessage //TODO: нам не нужно использовать механизм сообщений для прерывания локальных задач.

  /**
    * Execute interruptible task submitted by the remote node and reply to the {@param replyTo} actor.
    */
  def apply[R, A](
                   runtime: Runtime[R],
                   task: RIO[R, A],
                   replyTo: TaskReplyToActor.Ref[A],
                   deathWatches: ActorRef[Nothing]*
                 ): Behavior[Command[A]] = new TaskExecutor[R, A](runtime, task, deathWatches) {
    override protected final def done(r: Exit[Throwable, A]): Unit = replyTo ! TaskReplyToActor.Done(r)

    override protected final def error(e: Throwable): Unit = replyTo ! TaskReplyToActor.Done(Exit.fail(e))

    override protected final def terminated(actor: ActorRef[Nothing]): Unit = if (actor != replyTo) {
      replyTo ! TaskReplyToActor.Done(Exit.fail(actorTerminatedException(actor)))
    }
  }.behaviour

  /**
    * Execute interruptible task with local callback.
    */
  def apply[R, A](
                   runtime: Runtime[R],
                   task: RIO[R, A],
                   cb: Task[A] => Unit,
                   deathWatches: ActorRef[Nothing]*
                 ): Behavior[Command[A]] = new TaskExecutor[R, A](runtime, task, deathWatches) {
    override protected final def done(r: Exit[Throwable, A]): Unit = cb(Task.done(r))

    override protected final def error(e: Throwable): Unit = cb(Task.fail(e))

    override protected final def terminated(actor: ActorRef[Nothing]): Unit = cb(Task.fail(actorTerminatedException(actor)))
  }.behaviour
}