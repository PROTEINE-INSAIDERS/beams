package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._

private object SpawnLocal {

  sealed trait Command extends NonSerializableMessage

  private[akka] final case class Spawn[T](behavior: Behavior[T], cb: ActorRef[T] => Unit) extends Command

  private[akka] object Stop extends Command

  private[akka] def apply: Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case Spawn(behavior, cb) =>
        cb(ctx.spawnAnonymous(behavior))
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
