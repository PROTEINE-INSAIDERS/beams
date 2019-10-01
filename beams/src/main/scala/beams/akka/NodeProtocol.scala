package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import scalaz.zio._
import scalaz.zio.internal.PlatformLive

import scala.util.control.NonFatal

private[akka] object NodeProtocol {
  type Ref[-R] = ActorRef[Command[R]]

  type Key[R] = ServiceKey[Command[R]]

  sealed trait Command[+R]

  final case class Node[U](
                            f: AkkaBeam[U] => U,
                            key: Option[NodeProtocol.Key[U]],
                            cb: Task[NodeProtocol.Ref[U]] => Unit
                          ) extends Command[Nothing] with NonSerializableMessage

  final case class Nodes[U](
                             key: NodeProtocol.Key[U],
                             queue: Queue[Set[NodeProtocol.Ref[U]]],
                             cb: Task[ReceptionistListener.Ref] => Unit
                           ) extends Command[Nothing] with NonSerializableMessage

  object Shutdown extends Command[Nothing]

  def apply[R](f: AkkaBeam[R] => R, key: Option[NodeProtocol.Key[R]]): Behavior[Command[R]] = Behaviors.setup { ctx =>
    val runtime = Runtime(f(new AkkaBeam(ctx.self)), PlatformLive.fromExecutionContext(ctx.executionContext))
    key.foreach { k => ctx.system.receptionist.tell(Receptionist.Register(k, ctx.self)) }
    Behaviors.receiveMessagePartial {
      case Node(f, key, cb) =>
        try {
          cb(IO.succeed(ctx.spawnAnonymous(NodeProtocol(f, key))))
        } catch {
          case NonFatal(e) => cb(IO.fail(e))
        }
        Behaviors.same
      case Shutdown =>
        Behaviors.stopped
    }
  }
}
