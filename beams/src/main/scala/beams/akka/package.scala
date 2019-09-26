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
package object akka extends BeamsSyntax[AkkaNode.Ref] {
  /**
    * Create beams actor system.
    */
  def node[R: TypeTag](
                        systemName: String = "beams",
                        setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                        environment: ActorContext[AkkaNode.Command[R]] => R
                      ): Managed[Throwable, AkkaNode.Ref[R]] =
    Managed.make {
      IO {
        val system = ActorSystem(AkkaNode(environment), systemName, setup)
        val key = serviceKey[R]
        system.receptionist ! Receptionist.Register(key, system)
        system
      }
    } { system =>
      tellZio(system, AkkaNode.Stop) *> IO.effectTotal {
        val cluster = Cluster(system)
        cluster.manager ! Leave(cluster.selfMember.address)
      }
    }

  def serviceKey[R: TypeTag]: ServiceKey[AkkaNode.Command[R]] = {
    val uid = URLEncoder.encode(typeTag[R].tpe.toString, "UTF-8")
    ServiceKey[AkkaNode.Command[R]](s"beams-node-$uid")
  }

  override def submitTo[R](node: AkkaNode.Ref[R])(task: TaskR[R, Any]): Task[Unit] = tellZio(node, AkkaNode.Submit(task))

  private[akka] def tellZio[A](ref: ActorRef[A], a: A): UIO[Unit] = ZIO.effectTotal(ref.tell(a))
}
