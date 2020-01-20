package beams

import java.net.URLEncoder

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import _root_.akka.cluster.typed._
import zio._

import scala.reflect.runtime.universe
import scala.util.control.NonFatal

package object akka extends BeamsSyntax[AkkaEngine] {
  /**
   * Create beams root node.
   */
  def root[R](
               env: Beam[AkkaEngine] => R,
               key: Option[NodeActor.Key[R]] = None,
               name: String = "beams",
               setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
               props: Props = Props.empty,
             ): Managed[Throwable, ActorSystem[NodeActor.Command[R]]] = Managed.make {
    IO {
      ActorSystem(NodeActor(env), name, setup, props)
    }
  } { system =>
    Task.effectTotal {
      system ! LocalSpawnProtocol.Stop
      val cluster = Cluster(system)
      cluster.manager ! Leave(cluster.selfMember.address)
    }
  }

  def node[R](env: R, key: Option[NodeActor.Key[R]] = None): RManaged[ActorRef[LocalSpawnProtocol.Command], NodeActor.Ref[R]] =
    ZManaged.make {
      ZIO.accessM[ActorRef[LocalSpawnProtocol.Command]] { lsp =>
        ZIO.effectAsyncMaybe { cb: (Task[NodeActor.Ref[R]] => Unit) =>
          try {
            lsp ! LocalSpawnProtocol.Spawn(NodeActor(env), key match {
              case Some(k) => { task: Task[NodeActor.Ref[R]] => task.absolve ??? }
              case None => cb
            }

              ///  { task: Task[ExecutorActor.Ref[R]] =>
              //       cb(task.tap { node => IO(lsp.receptionist ! Receptionist.register(nodeKey[R], node))
              //       })
              //     }

            )
          } catch {
            case NonFatal(e) => Some(IO.fail(e))
          }
          None
        }
      }
    } { node => Task.effectTotal(node ! NodeActor.Stop) }

  def nodeKey[R: universe.TypeTag]: ServiceKey[NodeActor.Command[R]] = {
    val uid = URLEncoder.encode(universe.typeTag[R].tpe.toString, "UTF-8")
    ServiceKey[NodeActor.Command[R]](s"beams-node-$uid")
  }

  override def submitTo[U](node: NodeActor.Ref[U])(task: RIO[U, Any]): Task[Unit] = Task.effectTotal {
    ???
  }
}
