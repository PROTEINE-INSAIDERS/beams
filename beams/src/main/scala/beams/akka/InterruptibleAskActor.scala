package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

/**
  * Actor for interruptible ask pattern.
  */
object InterruptibleAskActor {

  object Interrupt

  def apply[Req, Res](
                       promise: Promise[Nothing, Res],
                       runtime: Runtime[_]
                     ): Behavior[Any] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case Interrupt =>
        runtime.unsafeRun(promise.interrupt)
        Behaviors.stopped
      case response =>
        runtime.unsafeRun(promise.succeed(response.asInstanceOf[Res]))
        Behaviors.stopped
    }
  }
}
