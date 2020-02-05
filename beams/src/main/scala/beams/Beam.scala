package beams

import zio._

trait Backend {
  type Node[+_]
}

trait Beam[X <: Backend] {
  def beam: Beam.Service[Any, X]
}

object Beam {

  trait Service[R, X <: Backend] {
    def announce(key: String): RIO[R, Unit]

    /**
     * Run task at specified node and waits for result.
     */
    def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[R, A]

    def deathwatch(node: X#Node[Any]): RIO[R, Unit]

    /**
     * List available nodes by given key.
     */
    def listing[U](key: String): RManaged[R, Queue[Set[X#Node[U]]]]

    /**
     * Create a child node which can run tasks.
     */
    def node[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[R, X#Node[U]]
  }

}

trait BeamsSyntax[X <: Backend] extends Beam.Service[Beam[X], X] {
  override def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[Beam[X], A] =
    ZIO.accessM(_.beam.at(node)(task))

  override def deathwatch(node: X#Node[Any]): RIO[Beam[X], Unit] = ZIO.accessM(_.beam.deathwatch(node))

  override def listing[U](key: String): RManaged[Beam[X], Queue[Set[X#Node[U]]]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.listing(key))

  override def node[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[Beam[X], X#Node[U]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.node(f))

  override def announce(key: String): RIO[Beam[X], Unit] = ZIO.accessM(_.beam.announce(key))

  /**
   * Wait till some nodes with given environment will become available.
   */
  def someNode[U](key: String): RIO[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
    r.beam.listing[U](key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)))
  }

  /**
   * Wait till any node with given environment will become available.
   */
  def anyNode[U](key: String): RIO[Beam[X], X#Node[U]] = someNode[U](key).map(_.head)
}