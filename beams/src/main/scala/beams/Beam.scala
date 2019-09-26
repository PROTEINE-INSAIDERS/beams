package beams

import scalaz.zio._
import scalaz.zio.clock.Clock

import scala.reflect.runtime.universe

trait Beam[N[+ _]] {
  def beams: Beam.Service[Any, N]
}

object Beam {

  trait Service[R, N[+ _]] {
    /**
      * List available nodes with given environment.
      */
    def nodeListing[U: universe.TypeTag]: ZManaged[R, Throwable, Queue[Set[N[U]]]]

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
    * Please note that the parent task will not track submitted tasks hence parent task interruption will not interrupt submitted tasks.
    */
  def submitTo[U](node: N[U])(task: TaskR[U, Any]): Task[Unit]

  override def nodeListing[U: universe.TypeTag]: ZManaged[Beam[N], Throwable, Queue[Set[N[U]]]] = ZManaged.environment[Beam[N]].flatMap(_.beams.nodeListing)

  override def runAt[U, A](node: N[U])(task: TaskR[U, A]): TaskR[Beam[N], A] = ZIO.accessM(_.beams.runAt(node)(task))

  /**
    * Wait till some nodes with given environment will become available.
    */
  def someNodes[U: universe.TypeTag]: TaskR[Beam[N], Set[N[U]]] = ZIO.accessM { r =>
    r.beams.nodeListing[U].use(_.take.repeat(Schedule.doUntil(_.nonEmpty)).provide(Clock.Live))
  }

  /**
    * Wait till any node with given environment will become available.
    */
  def anyNode[U: universe.TypeTag]: TaskR[Beam[N], N[U]] = someNodes.map(_.head)
}