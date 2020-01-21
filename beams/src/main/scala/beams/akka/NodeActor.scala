package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import zio._
import zio.internal.PlatformLive

import scala.util.control.NonFatal

object NodeActor {
  type Ref[+R] = ActorRef[Command[R]]

  type Key[R] = ServiceKey[Command[R]]

  private[akka] sealed trait Command[-R]

  private[akka] final case class Spawn[T](behavior: Behavior[T], key: Option[ServiceKey[T]], cb: Task[ActorRef[T]] => Unit)
    extends Command[Any] with NonSerializableMessage

  private[akka] final case class Exec[R, A](task: RIO[R, A], replyTo: ReplyToActor.Ref[A]) extends Command[R] with SerializableMessage

  private[akka] object Stop extends Command[Any] with NonSerializableMessage

  private[akka] def apply[R](f: Runtime[AkkaBeam] => Runtime[R]): Behavior[Command[R]] = Behaviors.setup { ctx =>
    val runtime = f(Runtime(AkkaBeam(ctx.self), PlatformLive.fromExecutionContext(ctx.executionContext)))

    Behaviors.receiveMessagePartial {
      case Spawn(behavior, key, cb) =>
        try {
          val ref = ctx.spawnAnonymous(behavior)
          key.foreach(ctx.system.receptionist ! Receptionist.Register(_, ref))
          cb(Task.succeed(ctx.spawnAnonymous(behavior)))
        } catch {
          case NonFatal(e) => cb(Task.fail(e))
        }
        Behaviors.same
      case Exec(task, replyTo) =>
        val taskActor = ctx.spawnAnonymous(TaskActor(runtime, task, replyTo))
        replyTo ! ReplyToActor.Register(taskActor)
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
