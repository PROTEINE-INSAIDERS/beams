package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

object NodeActor {
  type Ref = ActorRef[Message]

  sealed trait Message

  final case class RunTask(task: TaskR[Beam[AkkaNode, Any], Any], replyTo: ActorRef[TaskFinished]) extends Message

  final case class TaskFinished(exit: Exit[Throwable, Any]) extends Message

  def apply[Env](env: Env): Behavior[Message] = Behaviors.setup { _ =>
    val runtime = new DefaultRuntime {}
    Behaviors.receiveMessagePartial {
      case RunTask(task, replyTo) =>
        val beam = AkkaBeam[Env]()
        val program = task.asInstanceOf[TaskR[Beam[AkkaNode, Env], Any]].provide(beam)
        val result = runtime.unsafeRunSync(program)
        replyTo ! TaskFinished(result)
        Behaviors.same
    }
  }
}
