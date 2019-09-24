package beams.akka

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import scalaz.zio._
import scalaz.zio.internal.{Platform, PlatformLive}

/**
  * Top-level actor for a beams cluster's node.
  */
object BeamsSupervisor {
  type Ref[+R] = ActorRef[Command[R]]

  sealed trait Command[-R]

  //TODO: Возможно в целях оптимизации следует добавить сообщение, предназначенное для локального запуска задач.
  // replyTo в нём следует заменить call-back функцией.
  // Это позволит избежать создания доплнительных акторов для реализации ask-паттернов.
  final case class RunTask[R, A](
                                  task: TaskR[R, A],
                                  replyTo: ActorRef[Exit[Throwable, A]]
                                ) extends Command[R]

  object Shutdown extends Command[Any] with SerializableMessage

  //TODO: Убрать bootstrap.
  def apply[R](
                environment: ActorContext[Command[R]] => R,
                bootstrap: TaskR[R, Unit]
              ): Behavior[Command[R]] =
    Behaviors.setup { ctx =>
      val runtime = new Runtime[R] {
        override val Environment: R = environment(ctx)
        override val Platform: Platform = PlatformLive.fromExecutionContext(ctx.executionContext)
      }
      runtime.Environment match {
        case requiresActorThread: RequiresActorThread =>
          runtime.unsafeRunAsync_(bootstrap.ensuring(IO.effectTotal(requiresActorThread.releaseActorThread())))
          requiresActorThread.blockActorThread()
        case _ => runtime.unsafeRun(bootstrap)
          runtime.unsafeRun(bootstrap)
      }
      Behaviors.receiveMessagePartial {
        case Shutdown => Behaviors.stopped
      }
    }
}
