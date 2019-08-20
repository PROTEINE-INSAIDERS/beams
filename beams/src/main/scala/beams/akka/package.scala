package beams

import _root_.akka.actor.typed._
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.util.Timeout
import scalaz.zio._

package object akka {

  private[akka] final case class AskZioPartiallyApplied[Res](dummy: Boolean = true) extends AnyVal {
    def apply[Req](ref: ActorRef[Req], replyTo: ActorRef[Res] => Req)
                  (implicit timeLimit: TimeLimit, scheduler: Scheduler): Task[Res] = IO.fromFuture { _ =>
      implicit val timeout: Timeout = timeLimit.current()
      ref.ask[Res](replyTo)
    }
  }

  private[akka] def askZio[Res]: AskZioPartiallyApplied[Res] = AskZioPartiallyApplied[Res]()

  private[akka] def tellZio[A](ref: ActorRef[A], a: A): Canceler = ZIO.effectTotal(ref.tell(a))
}
