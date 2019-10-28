package beams.akka

import akka.actor.typed.receptionist.ServiceKey
import beams._
import scalaz.zio._

import scala.collection.JavaConverters

import scala.util.control.NonFatal

class AkkaBeam[R](self: NodeProtocol.Ref[R]) extends beams.Beam[AkkaBackend.type] {

  override def beam: Beam.Service[Any, AkkaBackend.type] = new Beam.Service[Any, AkkaBackend.type] {
    override def node[U](
                          f: beams.Beam[AkkaBackend.type] => U,
                          key: Option[NodeProtocol.Key[U]]
                        ): ZManaged[Any, Throwable, NodeProtocol.Ref[U]] = Managed.make {
      ZIO.effectAsync { cb: (Task[NodeProtocol.Ref[U]] => Unit) =>
        try {
          self ! NodeProtocol.Node(f, key, cb)
        } catch {
          case NonFatal(e) => cb(ZIO.fail(e))
        }
      }
    } { ref =>
      IO.effectTotal(ref ! NodeProtocol.Shutdown)
    }

    override def nodes[U](
                           key: ServiceKey[NodeProtocol.Command[U]]
                         ): ZManaged[Any, Throwable, Queue[Set[NodeProtocol.Ref[U]]]] = Managed.make {
      for {
        queue <- Queue.unbounded[Set[NodeProtocol.Ref[U]]]
        listener <- ZIO.effectAsync { cb: (Task[ReceptionistListener.Ref] => Unit) =>
          try {
            self ! NodeProtocol.Nodes(key, queue, cb)
          } catch {
            case NonFatal(e) => cb(ZIO.fail(e))
          }
        }
      } yield (queue, listener)
    } { case (_, listener) =>
      IO.effectTotal(listener ! ReceptionistListener.Shutdown)
    } map (_._1)

    override def runAt[U, A](node: NodeProtocol.Ref[U])(task: TaskR[U, A]): TaskR[Any, A] = Managed.make {
      ZIO.effectAsync { cb: (Task[HomunculusLoxodontus.Ref[A]] => Unit) =>
        try {
          self ! NodeProtocol.RunAt(node, task, cb)
        } catch {
          case NonFatal(e) => cb(ZIO.fail(e))
        }
      }
    } { homunculusLoxodontus =>
      IO.effectTotal(homunculusLoxodontus ! HomunculusLoxodontus.Shutdown)
    }.use { homunculusLoxodontus =>
      ZIO.effectAsyncInterrupt { cb: (Task[A] => Unit) =>
        try {
          homunculusLoxodontus ! HomunculusLoxodontus.Wait(cb)
        } catch {
          case NonFatal(e) => cb(ZIO.fail(e))
        }
        Left(IO.effectTotal {
          homunculusLoxodontus ! HomunculusLoxodontus.Interrupt
        })
      }
    }

    override def submitTo[U](node: NodeProtocol.Ref[U])(task: TaskR[U, Any]): TaskR[Any, Unit] =
      ZIO {
        node ! NodeProtocol.Submit(task)
      }
  }
}
