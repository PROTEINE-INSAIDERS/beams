package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import zio._

import scala.util.control.NonFatal

//TODO: возможно следует переделать в актор, дающий доступ к контексту.
//TODO: можно вообще эту функциональность в ноду добавить
object LocalSpawnProtocol {
  type Ref = ActorRef[Command]

  sealed trait Command extends NonSerializableMessage

  private[akka] final case class Spawn[T](behavior: Behavior[T], cb: Task[ActorRef[T]] => Unit) extends Command

  private[akka] object Stop extends Command

  private[akka] def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case Spawn(behavior, cb) =>
        try {
          cb(Task.succeed(ctx.spawnAnonymous(behavior)))
        } catch {
          case NonFatal(e) => cb(Task.fail(e))
        }
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
