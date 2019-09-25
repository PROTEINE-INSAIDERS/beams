package beams

import java.net.URLEncoder

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import _root_.akka.actor.typed.scaladsl._
import _root_.akka.cluster.typed._
import scalaz.zio._

import scala.reflect.runtime.universe._

//TODO: factor out cluster interface abstraction (where possible)
package object akka extends BeamsSyntax[Node.Ref] {
  /**
    * Create beams actor system.
    */
  def node[R: TypeTag](
                        systemName: String = "beams",
                        setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                        environment: ActorContext[Node.Command[R]] => R
                      ): Managed[Throwable, Node.Ref[R]] =
    Managed.make {
      IO {
        val system = ActorSystem(Node(environment), systemName, setup)
        val key = serviceKey[R]
        system.receptionist ! Receptionist.Register(key, system)
        system
      }
    } { system =>
      tellZio(system, Node.Stop) *> IO.effectTotal {
        val cluster = Cluster(system)
        cluster.manager ! Leave(cluster.selfMember.address)
      }
    }

  def serviceKey[R: TypeTag]: ServiceKey[Node.Command[R]] = {
    val uid = URLEncoder.encode(typeTag[R].tpe.toString, "UTF-8")
    ServiceKey[Node.Command[R]](s"beams-node-$uid")
  }

  def submit[R](node: Node.Ref[R], task: TaskR[R, Unit]): UIO[Unit] = tellZio(node, Node.Submit(task))

  private[akka] def tellZio[A](ref: ActorRef[A], a: A): UIO[Unit] = ZIO.effectTotal(ref.tell(a))
}
