package beams

import zio.RIO

trait Deathwatch[X <: Backend] {
  def deathwatch: Deathwatch.Service[Any, X]
}

object Deathwatch {

  trait Service[R, X <: Backend] {
    def deathwatch(node: X#Node[Any]): RIO[R, Unit]
  }

  trait Syntax[X <: Backend] extends Service[Deathwatch[X], X] {
    final override def deathwatch(node: X#Node[Any]): RIO[Deathwatch[X], Unit] =
      RIO.accessM(_.deathwatch.deathwatch(node))
  }

}
