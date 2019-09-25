package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import scalaz.zio._

private[akka] object ReceptionistListener {

  type Ref = ActorRef[Command]

  sealed trait Command extends NonSerializableMessage

  final case class Listing[T](listing: Receptionist.Listing) extends Command

  object Stop extends Command

  def apply[T](
                key: ServiceKey[T],
                queue: Queue[Set[ActorRef[T]]],
                runtime: Runtime[_]
              ): Behavior[Command] =
    Behaviors.setup { ctx =>
      val subscriber = ctx.messageAdapter[Receptionist.Listing](Listing(_))
      ctx.system.receptionist ! Receptionist.subscribe(key, subscriber)
      Behaviors.receiveMessagePartial {
        case Listing(key.Listing(services)) =>
          runtime.unsafeRun(queue.offer(services))
          Behaviors.same
        case Stop =>
          Behaviors.stopped { () => runtime.unsafeRun(queue.shutdown) }
      }
    }
}
