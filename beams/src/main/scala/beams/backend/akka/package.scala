package beams.backend

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.scaladsl._
import beams._
import zio._

import scala.util.control.NonFatal

package object akka extends Beam.Syntax[AkkaBackend] {
  /**
    * Create root node and run task on it.
    */
  def root[R, A](f: Runtime[AkkaBeam] => Runtime[R],
                 name: String = "beams",
                 setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                 props: Props = Props.empty
                )(task: RIO[R, A]): Task[A] =
    IO(ActorSystem(NodeActor(f), name, setup, props)).bracket {
      system => IO.effectTotal(system ! NodeActor.Stop)
    } { system =>
      AkkaBeam(system, system).execution.at(system)(task)
    }

  @inline
  @specialized
  private[akka] def guardAsync[A](f: (Task[A] => Unit) => Unit): Task[A] =
    Task.effectAsync[A] { cb =>
      try {
        f(cb)
      } catch {
        case NonFatal(e) => cb(Task.fail(e))
      }
    }

  @inline
  @specialized
  private[akka] def guardAsyncInterrupt[A](f: (Task[A] => Unit) => Either[Canceler[Any], Task[A]]): Task[A] =
    Task.effectAsyncInterrupt[A] { cb =>
      try {
        f(cb)
      } catch {
        case NonFatal(e) => Right(Task.fail(e))
      }
    }

  @inline
  @specialized
  private[akka] def guardBehavior[T](errorCb: Task[Nothing] => Unit, errorBehavior: Behavior[T] = Behaviors.stopped[T])
                                    (behavior: => Behavior[T]): Behavior[T] = {
    try {
      behavior
    } catch {
      case NonFatal(e) =>
        errorCb(Task.fail(e))
        errorBehavior
    }
  }

  @inline
  @specialized
  private[akka] def guardBehavior1[T](errorCb: Throwable => Unit, errorBehavior: Behavior[T] = Behaviors.stopped[T])
                                     (behavior: => Behavior[T]): Behavior[T] = {
    try {
      behavior
    } catch {
      case NonFatal(e) =>
        errorCb(e)
        errorBehavior
    }
  }
}
