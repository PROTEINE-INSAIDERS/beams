package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import zio._

private[akka] object DeathWatch {

  trait Command

  object Stop extends Command with NonSerializableMessage

  object Stopped extends Command with SerializableMessage

  def apply(actor: ActorRef[Nothing], cb: Task[Unit] => Unit): Behavior[Command] = Behaviors.setup { ctx =>
    guardBehavior(cb) {
      ctx.watchWith(actor, Stopped)

      Behaviors.receiveMessagePartial {
        case Stopped => guardBehavior(cb) {
          cb(Task.unit)
          Behaviors.stopped
        }
        case Stop => guardBehavior(cb) {
          ctx.unwatch(actor)
          Behaviors.stopped
        }
      }
    }
  }
}
