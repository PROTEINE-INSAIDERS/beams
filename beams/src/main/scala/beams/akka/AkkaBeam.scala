package beams.akka

import akka.actor.typed.receptionist.ServiceKey
import beams._
import scalaz.zio.{Task, _}

import scala.util.control.NonFatal

class AkkaBeam[R](self: NodeProtocol.Ref[R]) extends beams.Beam[Backend.type] {

  override def beam: Beam.Service[Any, Backend.type] = new Beam.Service[Any, Backend.type] {
    override def node[U](
                          f: beams.Beam[Backend.type] => U,
                          k: Option[ServiceKey[NodeProtocol.Command[U]]]
                        ): ZManaged[Any, Throwable, NodeProtocol.Ref[U]] = ZManaged.make {
      ZIO.effectAsync { cb: (Task[NodeProtocol.Ref[U]] => Unit) =>
        try {
          self ! NodeProtocol.Node(f, k, cb)
        } catch {
          case NonFatal(e) => cb(ZIO.fail(e))
        }
      }
    } { ref =>
      IO.effectTotal(ref ! NodeProtocol.Shutdown)
    }

    override def nodes[U](
                           k: ServiceKey[NodeProtocol.Command[U]]
                         ): ZManaged[Any, Throwable, Queue[Set[NodeProtocol.Ref[U]]]] = ZManaged.make {
      for {
        queue <- Queue.unbounded[Set[NodeProtocol.Ref[U]]]
        listener <- ZIO.effectAsync { cb: (Task[ReceptionistListener.Ref] => Unit) =>
          try {
            self ! NodeProtocol.Nodes(k, queue, cb)
          } catch {
            case NonFatal(e) => cb(ZIO.fail(e))
          }
        }
      } yield (queue, listener)
    } { case (_, listener) =>
      IO.effectTotal(listener ! ReceptionistListener.Shutdown)
    } map (_._1)

    override def runAt[U, A](n: NodeProtocol.Ref[U])(t: TaskR[U, A]): TaskR[Any, A] = ???

    override def submitTo[U](n: NodeProtocol.Ref[U])(t: TaskR[U, Any]): TaskR[Any, Unit] = ???
  }
}
