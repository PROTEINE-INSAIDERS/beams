package beams.backend.akka

import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import _root_.akka.actor.typed.scaladsl._
import zio._

private[akka] object ReceptionistListener {

  object Stop extends NonSerializableMessage

  def apply[T](
                key: ServiceKey[T],
                queue: Queue[Set[ActorRef[T]]],
                runtime: Runtime[Any]
              ): Behavior[Any] = Behaviors.setup { ctx =>
    //TODO: обработка ошибок?
    ctx.system.receptionist ! Receptionist.Subscribe(key, ctx.self)
    Behaviors.receiveMessagePartial {
      case key.Listing(services) =>
        runtime.unsafeRun(queue.offer(services))
        Behaviors.same
      case Stop =>
        Behaviors.stopped { () => runtime.unsafeRun(queue.shutdown) }
    }
  }
}