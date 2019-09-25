package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._

private[akka] object HomunculusLoxodontus {
  // type Ref[-B] = ActorRef[Command[B]]

  // sealed trait Command[+B] extends SerializableMessage

  // final case class Done[B](a: B) extends Command[B]

  def apply[A, B](ref: ActorRef[A], replyTo: ActorRef[B] => A, cb: B => Unit): Behavior[Any] = Behaviors.setup { ctx =>
    ref ! replyTo(ctx.self)
    Behaviors.receiveMessagePartial {
      case a =>
        cb(a.asInstanceOf[B])
        Behaviors.stopped
    }
  }
}
