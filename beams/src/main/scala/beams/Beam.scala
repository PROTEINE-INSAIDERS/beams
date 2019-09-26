package beams

import scalaz.zio._

import scala.annotation.tailrec
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

    //TODO: implement "fire and forget". Such task can not be canceled. No fiber, no cancelation however.
  }

}

trait BeamsSyntax[N[+ _]] extends Beam.Service[Beam[N], N] {
  override def listing[U: universe.TypeTag]: ZManaged[Beam[N], Throwable, Queue[Set[N[U]]]] = ZManaged.environment[Beam[N]].flatMap(_.beams.listing)

  override def runAt[U, A](node: N[U])(task: TaskR[U, A]): TaskR[Beam[N], A] = ZIO.accessM(_.beams.runAt(node)(task))

  /**
    * Wait till some nodes with given environment will become available.
    */
  def someNodes[U: universe.TypeTag]: TaskR[Beam[N], Set[N[U]]] = ZIO.accessM { r =>
    r.beams.listing[U].use { queue =>
      def takeSome: Task[Set[N[U]]] = queue.take.flatMap { nodes => if (nodes.isEmpty) takeSome else Task.succeed(nodes) }
      takeSome
    }
  }
}