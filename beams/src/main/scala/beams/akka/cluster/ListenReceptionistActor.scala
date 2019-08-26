package beams.akka.cluster

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import scalaz.zio._

// TODO: shutdown queue?
object ListenReceptionistActor {
  def apply[Service](key: ServiceKey[Service], queue: Queue[Set[ActorRef[Service]]]): Behavior[Receptionist.Listing] = Behaviors.setup { _ =>
    Behaviors.receiveMessagePartial {
      case key.Listing(l) =>
        queue.offer(l)
        Behaviors.same
    }
  }
}
