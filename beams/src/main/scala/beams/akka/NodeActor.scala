package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl._
import beams._
import zio.{RIO, Runtime}

object NodeActor {
  type Ref[+R] = ActorRef[Command[R]]

  type Key[R] = ServiceKey[Command[R]]

  private[akka] sealed trait Command[-R]

  private[akka] final case class Exec[R, A](task: RIO[R, A], replyTo: ReplyToActor.Ref[A]) extends Command[R] with SerializableMessage

  private[akka] object Stop extends Command[Any] with NonSerializableMessage

  private[akka] def apply[R](env: Beam[AkkaEngine] => R): Behavior[Command[R]] = Behaviors.setup { ctx =>
    ???
  }

  private[akka] def apply[R](runtime: Runtime[R]): Behavior[Command[R]] = Behaviors.setup { ctx =>
    // val runtime = Runtime((), PlatformLive.fromExecutionContext(ctx.executionContext))
    Behaviors.receiveMessagePartial {
      case Exec(task, replyTo) =>
        val taskActor = ctx.spawnAnonymous(TaskActor(runtime, task, replyTo))
        replyTo ! ReplyToActor.Register(taskActor)
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
