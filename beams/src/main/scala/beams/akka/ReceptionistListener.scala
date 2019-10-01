package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import scalaz.zio._

private[akka] object ReceptionistListener {

  type Ref = ActorRef[Command]

  sealed trait Command

  object Shutdown extends Command with NonSerializableMessage

  def apply[T](
                key: ServiceKey[T],
                queue: Queue[Set[ActorRef[T]]],
                runtime: Runtime[_]
              ): Behavior[Any] =
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Subscribe(key, ctx.self)
      Behaviors.receiveMessagePartial {
        case key.Listing(services) =>
          runtime.unsafeRun(queue.offer(services))
          Behaviors.same
        case Shutdown =>
          Behaviors.stopped { () => runtime.unsafeRun(queue.shutdown) }
      }
    }
}
