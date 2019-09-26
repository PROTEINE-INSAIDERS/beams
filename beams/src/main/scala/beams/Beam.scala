package beams

import scalaz.zio._
import scalaz.zio.clock.Clock

import scala.reflect.runtime.universe

trait Beam[N[+ _]] {
  def beams: Beam.Service[Any, N]
}

object Beam {

  trait Service[R, N[+ _]] {
    //TODO: rename
    def listing[U: universe.TypeTag]: ZManaged[R, Throwable, Queue[Set[N[U]]]]

    /**
      * Run task at specified node and waits for result.
      */
    def runAt[U, A](node: N[U])(task: TaskR[U, A]): TaskR[R, A]
  }

}

trait BeamsSyntax[N[+ _]] extends Beam.Service[Beam[N], N] {
  /**
    * Submit task to specified node and return immediately.
    *
    * This function does not require beam's context and can be used to submit tasks outside of node (e.g. can be used in bootstrap sequences).
    *
    * Please note that the parent task does not track submitted tasks hence parent task interruption will not interrupt submitted tasks.
    */
  def submitTo[U](node: N[U])(task: TaskR[U, Any]): Task[Unit]

  override def listing[U: universe.TypeTag]: ZManaged[Beam[N], Throwable, Queue[Set[N[U]]]] = ZManaged.environment[Beam[N]].flatMap(_.beams.listing)

  override def runAt[U, A](node: N[U])(task: TaskR[U, A]): TaskR[Beam[N], A] = ZIO.accessM(_.beams.runAt(node)(task))

  /**
    * Wait till some nodes with given environment will become available.
    */
  def someNodes[U: universe.TypeTag]: TaskR[Beam[N], Set[N[U]]] = ZIO.accessM { r =>
    r.beams.listing[U].use(_.take.repeat(Schedule.doUntil(_.nonEmpty)).provide(Clock.Live))
  }
}