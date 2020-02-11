package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Registered
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import akka.cluster.typed._
import zio._
import zio.internal.PlatformLive

object NodeActor {
  type Ref[+R] = ActorRef[Command[R]]

  type Key[R] = ServiceKey[Command[R]]

  def Key[R](id: String): ServiceKey[Command[R]] = ServiceKey[Command[R]](id)

  private[akka] sealed trait Command[-R]

  private[akka] final case class Exclusive(key: String, cb: Task[Option[ExclusiveActor.Ref]] => Unit) extends Command[Any] with NonSerializableMessage

  private[akka] final case class Exec[R, A](task: RIO[R, A], replyTo: TaskReplyToActor.Ref[A]) extends Command[R] with SerializableMessage

  private[akka] final case class Register(key: String, cb: Task[Unit] => Unit) extends Command[Any] with NonSerializableMessage

  private[akka] final case class Spawn[T](behavior: Behavior[T], cb: Task[ActorRef[T]] => Unit)
    extends Command[Any] with NonSerializableMessage

  private[akka] object Stop extends Command[Any] with NonSerializableMessage

  private[akka] def apply[R](f: Runtime[AkkaBeam] => Runtime[R]): Behavior[Command[R]] = Behaviors.setup { ctx =>
    val runtime = f(Runtime(AkkaBeam(ctx.self, ctx.system), PlatformLive.fromExecutionContext(ctx.executionContext)))
    Behaviors.receiveMessagePartial {
      case Exclusive(key, cb) => guardBehavior(cb, Behaviors.same[Command[R]]) {
        // TODO:
        // 1. получить ExclusiveActor
        // 2. зарегистрировать задачу
        // 3. запустить задачу
        // 4. наблюдать ExclusiveActor, если он сдохнет, прервать задачу.


        val clusterSingleton = ClusterSingleton(ctx.system)
        val exclusive = clusterSingleton.init(SingletonActor(ExclusiveActor(), "beams-exclusive"))
        val replyTo = ctx.spawnAnonymous(ReplyToActor(exclusive, { t: Task[Boolean] => ??? } )) //TODO: should we watch replyTo?
        exclusive ! ExclusiveActor.Register(key, replyTo)
        Behaviors.same


      }
      case Exec(task, replyTo) =>
        val taskActor = ctx.spawnAnonymous(TaskActor(runtime, task, replyTo, replyTo))
        replyTo ! TaskReplyToActor.Register(taskActor)
        Behaviors.same
      case Register(key, cb) => guardBehavior(cb, Behaviors.same[Command[R]]) {
        val replyTo = ctx.spawnAnonymous(ReplyToActor(ctx.system.receptionist, { t: Task[Registered] => cb(t *> Task.unit) }))
        ctx.system.receptionist ! Receptionist.Register(ServiceKey[NodeActor.Command[R]](key), ctx.self, replyTo)
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
