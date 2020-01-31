package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Registered
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import zio._
import zio.internal.PlatformLive

object NodeActor {
  type Ref[+R] = ActorRef[Command[R]]

  type Key[R] = ServiceKey[Command[R]]

  def Key[R](id: String): ServiceKey[Command[R]] = ServiceKey[Command[R]](id)

  private[akka] sealed trait Command[-R]

  private[akka] final case class Spawn[T](behavior: Behavior[T], cb: Task[ActorRef[T]] => Unit)
    extends Command[Any] with NonSerializableMessage

  private[akka] final case class Exec[R, A](task: RIO[R, A], replyTo: ReplyToActor.Ref[A]) extends Command[R] with SerializableMessage

  private[akka] final case class Register(key: String, cb: Task[Unit] => Unit) extends Command[Any] with NonSerializableMessage

  private[akka] object Stop extends Command[Any] with NonSerializableMessage

  private[akka] def apply[R](f: Runtime[AkkaBeam] => Runtime[R]): Behavior[Command[R]] = Behaviors.setup { implicit ctx =>
    val runtime = f(Runtime(AkkaBeam(ctx.self), PlatformLive.fromExecutionContext(ctx.executionContext)))
    Behaviors.receiveMessagePartial {
      case Exec(task, replyTo) =>
        val taskActor = ctx.spawnAnonymous(TaskActor(runtime, task, replyTo))
        replyTo ! ReplyToActor.Register(taskActor)
        Behaviors.same
      case Register(key, cb) => guardBehavior(cb, Behaviors.same[Command[R]]) {
        ctx.system.receptionist ! Receptionist.Register(ServiceKey[NodeActor.Command[R]](key), ctx.self, replyTo { t: Task[Registered] => cb(t *> Task.unit) })
        Behaviors.same
      }
      case Spawn(behavior, cb) => guardBehavior(cb, Behaviors.same[Command[R]]) {
        cb(Task.succeed(ctx.spawnAnonymous(behavior)))
        Behaviors.same
      }
      case Stop =>
        Behaviors.stopped
    }
  }
}
