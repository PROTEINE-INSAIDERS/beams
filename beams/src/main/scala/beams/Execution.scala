package beams

import zio._

trait Execution[X <: Backend] {
  def execution: Execution.Service[Any, X]
}

object Execution {

  trait Service[R, X <: Backend] {
    /**
     * Run task at specified node and waits for result.
     */
    def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[R, A]
  }

  trait Syntax[X <: Backend] extends Service[Execution[X], X] {
    final override def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[Execution[X], A] =
      RIO.accessM(_.execution.at(node)(task))
  }

}