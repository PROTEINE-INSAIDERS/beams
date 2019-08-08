package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

object NodeActor {
  type Ref = ActorRef[Command]

  sealed trait Command

  final case class RunTask(task: TaskR[Beam[AkkaNode, Any], Any], replyTo: ActorRef[TaskFinished]) extends Command

  final case class TaskFinished(exit: Exit[Throwable, Any]) extends Command

  object Stop extends Command

  def apply[Env](env: Env): Behavior[Command] = Behaviors.setup { context =>
    val runtime = new DefaultRuntime {}
    val node = AkkaNode(env, context.self)
    Behaviors.receiveMessagePartial {
      case RunTask(task, replyTo) =>
        val beam = AkkaBeam[Env](node)
        val program = task.asInstanceOf[TaskR[Beam[AkkaNode, Env], Any]].provide(beam)
        val result = runtime.unsafeRunSync(program)
        replyTo ! TaskFinished(result)
        Behaviors.same
    }
  }
}
