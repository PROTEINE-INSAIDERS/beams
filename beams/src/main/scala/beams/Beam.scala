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

    //TODO: rename (run at.. whatever)
    def forkTo[U, A](node: N[U])(task: TaskR[U, A]): TaskR[R, Fiber[Throwable, A]]

    //TODO: implement "fire and forget". Such task can not be canceled. No fiber, no cancelation however.
  }
}

trait BeamsSyntax[N[+ _]] extends Beam.Service[Beam[N], N] {
  override def listing[U: universe.TypeTag]: ZManaged[Beam[N], Throwable, Queue[Set[N[U]]]] = ZManaged.environment[Beam[N]].flatMap(_.beams.listing)

  override def forkTo[U, A](node: N[U])(task: TaskR[U, A]): TaskR[Beam[N], Fiber[Throwable, A]] = ZIO.accessM(_.beams.forkTo(node)(task))
}