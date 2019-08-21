package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

object NodeActor {
  type Ref[+Env] = ActorRef[Command[Env]]

  sealed trait Command[-Env] extends BeamMessage

  final case class CreateNode[Env](env: Env, replyTo: ActorRef[Ref[Env]]) extends Command[Any]

  final case class RunTask[Env, A](
                                    task: TaskR[Beam[AkkaNode, Env], A],
                                    replyTo: ActorRef[Exit[Throwable, A]],
                                    timeLimit: TimeLimitContainer
                                  ) extends Command[Env]

  object Stop extends Command[Any]

  def apply[Env](env: Env, runtime: DefaultRuntime): Behavior[Command[Env]] = Behaviors.setup { ctx =>
    val self = AkkaNode[Env](ctx.self)
    Behaviors.receiveMessagePartial {
      case RunTask(task, replyTo, TimeLimitContainer(timeLimit)) =>
        val beam = AkkaBeam[Env](self, env, timeLimit, ctx.system.scheduler)
        val program = task.provide(beam)
        runtime.unsafeRunAsync(program)(result => replyTo ! result)
        Behaviors.same

      case CreateNode(env, replyTo) =>
        replyTo ! ctx.spawnAnonymous(NodeActor(env, runtime), Props.empty)
        Behaviors.same

      case Stop =>
        Behaviors.stopped
    }
  }
}
