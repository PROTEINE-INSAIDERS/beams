package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import zio._

private[akka] object TaskActor {
  type Ref[A] = ActorRef[Command[A]]

  sealed trait Command[+A]

  /**
    * Essential actor terminated.
    */
  final case class ActorTerminated(ref: ActorRef[Nothing]) extends Command[Nothing]

  /**
    * Task completed. Sent by the actor to itself.
    */
  final case class Done[A](exit: Exit[Throwable, A]) extends Command[A] with NonSerializableMessage

  /**
    * Interrupt the task. Sent by the reply-to actor.
    */
  object Interrupt extends Command[Nothing] with SerializableMessage

  private def interrupting[A]: Behavior[Command[A]] = Behaviors.receiveMessagePartial {
    case Done(_) => Behaviors.stopped // shutdown after the task interruption
    case _: Command[_] => Behaviors.same // swallow all other messages
  }

  /**
    * Execute interruptible task submitted by the remote node and reply to {@code replyTo} actor.
    */
  def apply[R, A](
                   runtime: Runtime[R],
                   task: RIO[R, A],
                   replyTo: TaskReplyToActor.Ref[A],
                   deathWatch: ActorRef[Nothing]*
                 ): Behavior[Command[A]] =
    apply(runtime, task, { t => replyTo ! TaskReplyToActor.Done(runtime.unsafeRunSync(t)) }, deathWatch: _*)

  /**
    * Execute interruptible task with local callback.
    */
  def apply[R, A](
                   runtime: Runtime[R],
                   task: RIO[R, A],
                   cb: Task[A] => Unit,
                   deathWatch: ActorRef[Nothing]*
                 ): Behavior[Command[A]] = Behaviors.setup { ctx =>
    guardBehavior(cb) {
      deathWatch.foreach { actor => ctx.watchWith(actor, ActorTerminated(actor)) }

      val taskFiber = runtime.unsafeRun(task.fork)
      runtime.unsafeRunAsync(taskFiber.join)(ctx.self ! Done(_))

      Behaviors.receiveMessagePartial {
        case ActorTerminated(actor) => guardBehavior(cb) {
          cb(Task.fail(new ActorTerminatedException("Essential actor terminated before completing the task.", actor)))
          runtime.unsafeRun(taskFiber.interrupt)
          deathWatch.foreach(ctx.unwatch)
          interrupting
        }
        case Done(exit) => guardBehavior(cb) {
          cb(Task.done(exit))
          deathWatch.foreach(ctx.unwatch)
          Behaviors.stopped
        }
        case Interrupt => guardBehavior(cb) {
          // cb(Task.interrupt) The interrupt message is sent by the reply-to actor in response to interruption. No need to invoke callback here.
          runtime.unsafeRun(taskFiber.interrupt)
          deathWatch.foreach(ctx.unwatch)
          interrupting
        }
      }
    }
  }
}
