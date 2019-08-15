package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

object NodeActor {
  type Ref = ActorRef[Command]
  type Ctx = ActorContext[Command]

  sealed trait Command extends BeamMessage

  final case class CreateNode(env: Any, replyTo: ActorRef[Ref]) extends Command

  final case class RunTask(
                            task: TaskR[Beam[AkkaNode, Any], Any],
                            replyTo: ActorRef[Exit[Throwable, Any]],
                            timeout: MigratableTimeout
                          ) extends Command

  object Stop extends Command

  def apply[Env](env: Env): Behavior[Command] = NodeActor(env, new DefaultRuntime {})

  private def apply[Env](env: Env, runtime: DefaultRuntime): Behavior[Command] = Behaviors.setup { ctx =>
    val self = AkkaNode[Env](ctx.self)
    Behaviors.receiveMessagePartial {
      case RunTask(task, replyTo, timeout) =>
        val beam = AkkaBeam[Env](self, env, timeout.local(), ctx.system.scheduler)
        val program = task.asInstanceOf[TaskR[Beam[AkkaNode, Env], Any]].provide(beam)
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
