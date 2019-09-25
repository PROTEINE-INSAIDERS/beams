package beams

import scalaz.zio._

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

trait Beam[N[+ _]] {
  def beams: Beam.Service[Any, N]
}

object Beam {

  trait Service[R, N[+ _]] {
    //TODO: rename
    def listing[U: TypeTag]: ZManaged[R, Throwable, Queue[Set[N[U]]]]

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
}