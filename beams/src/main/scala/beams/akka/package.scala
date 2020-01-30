package beams

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import _root_.akka.actor.typed.scaladsl._
import zio._

import scala.util.control.NonFatal

package object akka extends BeamsSyntax[AkkaBackend] {
  /**
   * Create root node and run task on it.
   */
  def root[R, A](f: Runtime[AkkaBeam] => Runtime[R],
                 key: Option[NodeActor.Key[R]] = None,
                 name: String = "beams",
                 setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                 props: Props = Props.empty
                )(task: RIO[R, A]): Task[A] = IO {
    val system = ActorSystem(NodeActor(f), name, setup, props)
    key.foreach(system.receptionist ! Receptionist.Register(_, system))
    system
  }.bracket { system => IO.effectTotal(system ! NodeActor.Stop) } { system =>
    AkkaBeam(system).beam.at(system)(task)
  }

  private[akka] def guard[T](cb: Task[Nothing] => Unit)(behavior: => Behavior[T]): Behavior[T] = {
    try {
      behavior
    } catch {
      case NonFatal(e) =>
        cb(Task.fail(e))
        Behaviors.stopped
    }
  }
}
