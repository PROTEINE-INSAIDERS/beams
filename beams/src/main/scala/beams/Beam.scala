package beams

import zio._

import scala.reflect.ClassTag

trait Backend {
  type Node[+_]
  type Key[_]
}

trait Beam[X <: Backend] {
  def beam: Beam.Service[Any, X]

  System.nanoTime()
}

object Beam {

  trait Service[R, X <: Backend] {
    /**
     * Run task at specified node and waits for result.
     */
    def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[R, A]

    /**
     * Create node key for the specified id.
     */
    def key[U: ClassTag](id: String): RIO[R, X#Key[U]]

    /**
     * List available nodes by given key.
     */
    def listing[U](key: X#Key[U]): RManaged[R, Queue[Set[X#Node[U]]]]

    /**
     * Create a child node which can run tasks.
     */
    def node[U](f: Runtime[Beam[X]] => Runtime[U], key: Option[X#Key[U]] = None): RManaged[R, X#Node[U]]
  }
}

trait BeamsSyntax[X <: Backend] extends Beam.Service[Beam[X], X] {
  override def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[Beam[X], A] =
    ZIO.accessM(_.beam.at(node)(task))

  override def key[U: ClassTag](id: String): RIO[Beam[X], X#Key[U]] = ZIO.accessM(_.beam.key(id))

  override def listing[U](key: X#Key[U]): RManaged[Beam[X], Queue[Set[X#Node[U]]]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.listing(key))

  override def node[U](f: Runtime[Beam[X]] => Runtime[U], key: Option[X#Key[U]] = None): RManaged[Beam[X], X#Node[U]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.node(f, key))

  /**
   * Wait till some nodes with given environment will become available.
   */
  def someNodes[U](key: X#Key[U]): RIO[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
    r.beam.listing[U](key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)))
  }

  /**
   * Wait till any node with given environment will become available.
   */
  def anyNode[U](key: X#Key[U]): RIO[Beam[X], X#Node[U]] = someNodes(key).map(_.head)
}