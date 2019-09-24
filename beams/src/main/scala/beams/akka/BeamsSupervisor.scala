package beams.akka

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import scalaz.zio._
import scalaz.zio.internal.{Platform, PlatformLive}

import scala.collection._

/**
  * Top-level actor for a beams cluster's node.
  */
object BeamsSupervisor {
  type Ref[+R] = ActorRef[Command[R]]

  sealed trait Command[-R]

  //TODO: Возможно в целях оптимизации следует добавить сообщение, предназначенное для локального запуска задач.
  // replyTo в нём следует заменить call-back функцией.
  // Это позволит избежать создания доплнительных акторов для реализации ask-паттернов.
  private final case class Exec[R, A](
                                       task: TaskR[R, A],
                                       replyTo: ActorRef[Exit[Throwable, A]]
                                     ) extends Command[R] with SerializableMessage

  private final case class RegisterFiber(fiber: Fiber[_, _], initiator: ActorRef[_], done: Task[Unit] => Unit) extends Command[Any] with NonSerializableMessage

  private final case class UnregisterFiber(initiator: ActorRef[_]) extends Command[Any] with NonSerializableMessage

  private final case class Submit[R, A](task: TaskR[R, A]) extends Command[R] with SerializableMessage

  private[akka] object Shutdown extends Command[Any] with SerializableMessage

  private[akka] def apply[R](environment: ActorContext[Command[R]] => R): Behavior[Command[R]] =
    Behaviors.setup { ctx =>
      val runtime = new Runtime[R] {
        override val Environment: R = environment(ctx)
        override val Platform: Platform = PlatformLive.fromExecutionContext(ctx.executionContext)
      }
      val fibers = mutable.HashMap[ActorRef[_], Fiber[_, _]]()
      Behaviors.receiveMessagePartial {
        case Exec(task, replyTo) =>
          val registerFiber = for {
            fiber <- task.fork
            _ <- Task.effectAsync { (cb: Task[Unit] => Unit) => ctx.self.tell(RegisterFiber(fiber, replyTo, cb)) }
          } yield fiber
          val unregisterFiber = tellZio(ctx.self, UnregisterFiber(replyTo))
          runtime.unsafeRunAsync(registerFiber.bracket(_ => unregisterFiber)(_.join))(replyTo.tell)
          Behaviors.same
        case RegisterFiber(fiber, initiator, done) =>
          fibers += initiator -> fiber
          done(Task.succeed(()))
          Behaviors.same
        case UnregisterFiber(initiator) =>
          fibers -= initiator
          Behaviors.same
        case Submit(task) =>
          runtime.unsafeRunAsync_(task)
          Behaviors.same
        case Shutdown =>
          Behaviors.stopped { () =>
            fibers.values.foreach(fiber => runtime.unsafeRun(fiber.interrupt))
          }
      }
    }
}
