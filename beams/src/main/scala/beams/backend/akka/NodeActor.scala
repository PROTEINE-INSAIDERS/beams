package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import beams.Beam
import zio._
import zio.internal.PlatformLive

object NodeActor {
  type Ref[+R] = ActorRef[Command[R]]

  type Key[R] = ServiceKey[Command[R]]

  def Key[R](id: String): ServiceKey[Command[R]] = ServiceKey[Command[R]](id)

  private[akka] sealed trait Command[-R]

  private[akka] final case class Exec[R, A](task: RIO[R, A], replyTo: TaskReplyToActor.Ref[A]) extends Command[R] with SerializableMessage

  private[akka] final case class ExecLocal[R, A](
                                                  task: RIO[R, A],
                                                  cb: Task[A] => Unit,
                                                  interrupt: Promise[Nothing, Any]
                                                ) extends Command[R] with NonSerializableMessage

  private[akka] final case class Run(runnable: Runnable) extends Command[Any] with NonSerializableMessage

  private[akka] object Stop extends Command[Any] with NonSerializableMessage

  private[akka] def apply[R](f: Runtime[Beam[AkkaBackend]] => Runtime[R]): Behavior[Command[R]] = Behaviors.setup { ctx =>
    val runtime = f(Runtime(AkkaBeam()(ctx, new NodeExecutionContext(ctx.self)), PlatformLive.fromExecutionContext(ctx.executionContext)))
    Behaviors.receiveMessagePartial {
      case Exec(task, replyTo) =>
        val taskActor = ctx.spawnAnonymous(TaskExecutor(runtime, task, replyTo, replyTo))
        replyTo ! TaskReplyToActor.Register(taskActor)
        Behaviors.same
      case ExecLocal(task, cb, interrupt) => guardBehavior(cb) {
        val wrapper = for {
          fiber <- task.fork
          _ <- (interrupt.await *> fiber.interrupt).fork
          result <- fiber.join
        } yield result
        runtime.unsafeRunAsync(wrapper)(k => cb(Task.done(k)))
        Behaviors.same
      }
      case Run(runnable) =>
        runnable.run()
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
