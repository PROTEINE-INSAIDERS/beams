package beams

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import zio._

import scala.reflect._

package object akka extends BeamsSyntax[AkkaBackend] {
  //TODO: перенести в beam!
  def key[R: ClassTag](id: String): Task[NodeActor.Key[R]] = IO(ServiceKey[NodeActor.Command[R]](id))

  /**
   * Create root node and run task on it.
   */
  def root[R, A](f: Runtime[AkkaBeam] => Runtime[R],
                 key: Option[NodeActor.Key[R]] = None,
                 name: String = "beams",
                 setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                 props: Props = Props.empty
                )(task: RIO[R, A]): Task[A] = {
    ???
  }

  /*
  Managed.make {
  for {
    system <- IO {
      val system = ActorSystem(NodeActor(f), name, setup, props)
      key.foreach(system.receptionist ! Receptionist.Register(_, system))
      system
    }
    _ <- IO.runtime
  } yield system
} { system =>
  Task.effectTotal {
    system ! NodeActor.Stop
  }
}.map(AkkaBeam(_)) */
}
