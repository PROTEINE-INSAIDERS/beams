package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import zio._

private[akka] object TaskActor {
  type Ref[A] = ActorRef[Command[A]]

  sealed trait Command[+A]

  /**
   * Task completed. Sent by the actor to itself.
   */
  final case class Done[A](exit: Exit[Throwable, A]) extends Command[A] with NonSerializableMessage

  /**
   * Interrupt the task. Sent by the reply-to actor.
   */
  object Interrupt extends Command[Nothing] with SerializableMessage

  /**
   * Reply-to actor terminated.
   */
  object ReplyToTerminated extends Command[Nothing]

  //TODO: возможно добавить try-catch и перенаправлять ошибки в replyTo
  def apply[R, A](runtime: Runtime[R], task: RIO[R, A], replyTo: ReplyToActor.Ref[A]): Behavior[Command[A]] =
    Behaviors.setup { ctx =>
      ctx.watchWith(replyTo, ReplyToTerminated)
      val fiber = runtime.unsafeRun(task.fork)
      runtime.unsafeRunAsync(fiber.join)(ctx.self ! Done(_))

      Behaviors.receiveMessagePartial {
        case Done(exit) => // Task completed.
          replyTo ! ReplyToActor.Done(exit)
          ctx.unwatch(replyTo)
          Behaviors.stopped
        case Interrupt => // Interrupt task
          runtime.unsafeRun(fiber.interrupt)
          ctx.unwatch(replyTo)
          Behaviors.receiveMessagePartial { // Ignore done message when interrupted
            case Done(_) => Behaviors.stopped
          }
        case ReplyToTerminated => // Reply-to terminated. Interrupt task and ignore result.
          runtime.unsafeRun(fiber.interrupt)
          Behaviors.receiveMessagePartial {
            case Done(_) => Behaviors.stopped
          }
      }
    }
}
