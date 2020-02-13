package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._

import scala.collection._

private[akka] object ExclusiveActor {
  type Ref = ActorRef[Command]

  trait Command

  final case class Register(key: String, replyTo: ActorRef[Boolean]) extends Command with SerializableMessage

  final case class Unregister(key: String) extends Command with SerializableMessage

  /**
   * Singleton actor which coordinates exclusive execution. Since this actor not bound to particular node, it only
   * used to register and unregister exclusive task keys.
   */
  def apply[A](): Behavior[Command] = Behaviors.setup { _ =>
    val exclusives = new mutable.HashSet[String]()

    Behaviors.receiveMessagePartial {
      case Register(key, replyTo) =>
        replyTo ! exclusives.add(key)
        Behaviors.same
      case Unregister(key) =>
        exclusives.remove(key)
        Behaviors.same
    }
  }
}
