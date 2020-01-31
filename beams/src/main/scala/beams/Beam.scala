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
    def announceNode(key: String): RIO[R, Unit]

    /**
     * Run task at specified node and waits for result.
     */
    def atNode[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[R, A]

    def deathwatchNode(node: X#Node[Any]): RIO[R, Unit]

    /**
     * List available nodes by given key.
     */
    def listNodes[U](key: String): RManaged[R, Queue[Set[X#Node[U]]]]

    /**
     * Create a child node which can run tasks.
     */
    def createNode[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[R, X#Node[U]]
  }

}

trait BeamsSyntax[X <: Backend] extends Beam.Service[Beam[X], X] {
  override def atNode[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[Beam[X], A] =
    ZIO.accessM(_.beam.atNode(node)(task))

  override def deathwatchNode(node: X#Node[Any]): RIO[Beam[X], Unit] = ZIO.accessM(_.beam.deathwatchNode(node))

  override def listNodes[U](key: String): RManaged[Beam[X], Queue[Set[X#Node[U]]]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.listNodes(key))

  override def createNode[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[Beam[X], X#Node[U]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.createNode(f))

  override def announceNode(key: String): RIO[Beam[X], Unit] = ZIO.accessM(_.beam.announceNode(key))

  /**
   * Wait till some nodes with given environment will become available.
   */
  def someNode[U](key: String): RIO[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
    r.beam.listNodes[U](key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)))
  }

  /**
   * Wait till any node with given environment will become available.
   */
  def anyNode[U](key: String): RIO[Beam[X], X#Node[U]] = someNode[U](key).map(_.head)
}