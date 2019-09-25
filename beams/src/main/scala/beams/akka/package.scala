package beams

import java.net.URLEncoder

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import _root_.akka.actor.typed.scaladsl._
import scalaz.zio._

import scala.reflect.runtime.universe._

//TODO: factor out cluster interface abstraction (where possible)
package object akka extends HasSpawnProtocolSyntax with HasReceptionistSyntax with ActorContextAccessorSyntax with BeamsSyntax[BeamsSupervisor.Ref] {
  /**
    * Create beams actor system.
    */
  def node[R: TypeTag](
                        systemName: String = "beams",
                        setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                        environment: ActorContext[BeamsSupervisor.Command[R]] => R
                      ): Managed[Throwable, BeamsSupervisor.Ref[R]] =
    Managed.make(IO {
      val system = ActorSystem(BeamsSupervisor(environment), systemName, setup)
      val key = serviceKey[R]
      system.receptionist ! Receptionist.Register(key, system)
      system
    })(tellZio(_, BeamsSupervisor.Shutdown))

  def serviceKey[R: TypeTag]: ServiceKey[BeamsSupervisor.Command[R]] = {
    val uid = URLEncoder.encode(typeTag[R].tpe.toString, "UTF-8")
    ServiceKey[BeamsSupervisor.Command[R]](s"beams-node-$uid")
  }

  def submit[R](node: BeamsSupervisor.Ref[R], task: TaskR[R, Unit]): UIO[Unit] = tellZio(node, BeamsSupervisor.Submit(task))

  private[akka] def tellZio[A](ref: ActorRef[A], a: A): UIO[Unit] = ZIO.effectTotal(ref.tell(a))
}
