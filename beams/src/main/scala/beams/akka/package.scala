package beams

import java.net.URLEncoder

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import _root_.akka.cluster.typed._
import scalaz.zio._

import scala.reflect.runtime.universe
import scala.util.control.NonFatal

package object akka {

/*

  /**
    * Create beams actor system.
    */
  def actorSystem(
                   name: String = "beams",
                   setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup())
                 ): Managed[Throwable, ActorSystem[LocalSpawnProtocol.Command]] = Managed.make {
    IO {
      ActorSystem(LocalSpawnProtocol(), name, setup)
    }
  } { system =>
    tellZIO(system, LocalSpawnProtocol.Stop) *> IO.effectTotal {
      val cluster = Cluster(system)
      cluster.manager ! Leave(cluster.selfMember.address)
    }
  }

  def node[R: universe.TypeTag](f: Beam[NodeActor.Ref] => R): ZManaged[ActorSystem[LocalSpawnProtocol.Command], Throwable, NodeActor.Ref[R]] = Managed.make {
    ZIO.accessM[ActorSystem[LocalSpawnProtocol.Command]] { system =>
      ZIO.effectAsyncMaybe { cb: (Task[NodeActor.Ref[R]] => Unit) =>
        try {
          system ! LocalSpawnProtocol.Spawn(NodeActor(f), { task: Task[NodeActor.Ref[R]] =>
            cb(task.tap { node => IO(system.receptionist ! Receptionist.register(nodeKey[R], node))
            })
          })
        } catch {
          case NonFatal(e) => Some(IO.fail(e))
        }
        None
      }
    }
  } { node => tellZIO(node, NodeActor.Stop) }

  def nodeKey[R: universe.TypeTag]: ServiceKey[NodeActor.Command[R]] = {
    val uid = URLEncoder.encode(universe.typeTag[R].tpe.toString, "UTF-8")
    ServiceKey[NodeActor.Command[R]](s"beams-node-$uid")
  }

  override def submitTo[R](node: NodeActor.Ref[R])(task: TaskR[R, Any]): Task[Unit] = tellZIO(node, NodeActor.Submit(task))

  private[akka] def tellZIO[A](ref: ActorRef[A], a: A): UIO[Unit] = ZIO.effectTotal(ref.tell(a))

*/
}
