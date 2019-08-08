package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

object NodeActor {
  type Ref = ActorRef[Command]
  type Ctx = ActorContext[Command]

  sealed trait Command extends AkkaMessage

  final case class CreateNode(env: Any) extends Command

  final case class RunTask(task: TaskR[Beam[AkkaNode, Any], Any], replyTo: ActorRef[TaskFinished]) extends Command

  final case class TaskFinished(exit: Exit[Throwable, Any]) extends Command

  object Stop extends Command

  def apply[Env](env: Env): Behavior[Command] = Behaviors.setup { ctx =>
    val runtime = new DefaultRuntime {}
    Behaviors.receiveMessagePartial {
      case CreateNode(env) =>
        val a = ctx.spawn(NodeActor(env), "")
        ???

      case RunTask(task, replyTo) =>
        val beam = AkkaBeam[Env](ctx.self, env)
        val program = task.asInstanceOf[TaskR[Beam[AkkaNode, Env], Any]].provide(beam)
        val result = runtime.unsafeRunSync(program)
        replyTo ! TaskFinished(result)
        Behaviors.same

      case Stop =>
        Behaviors.stopped
    }
  }
}
