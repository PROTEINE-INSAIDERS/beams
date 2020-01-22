package beams

import zio._

trait Backend {
  type Node[+_]
  type NodeKey[_]
}

trait Beam[X <: Backend] {
  def beam: Beam.Service[Any, X]
}

object Beam {

  trait Service[R, X <: Backend] {
    /**
     * Run task at specified node and waits for result.
     */
    def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[R, A]

    /**
     * List available nodes by given key.
     */
    def listing[U](key: X#NodeKey[U]): RManaged[R, Queue[Set[X#Node[U]]]]

    /**
     * Create a child node which can run tasks.
     */
    // При создании узла нам в любом случае надо передавать ему окружение, которое будут видеть задачи, отправленные на этот узел.
    // Кроме того, мы можем захотеть внести изменения в платформу.
    // Скорее всего нам понадобится сервиси самой Beam[X]
    def node[U](f: Runtime[Beam[X]] => Runtime[U], key: Option[X#NodeKey[U]] = None): RManaged[R, X#Node[U]]
  }

}

trait BeamsSyntax[X <: Backend] extends Beam.Service[Beam[X], X] {

  implicit class TaskExtension[U, A](task: RIO[U, A]) {
    def @@(node: X#Node[U]): RIO[Beam[X], A] = ZIO.accessM(_.beam.at(node)(task))

    def aaa(): Unit = ()
  }

  override def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[Beam[X], A] =
    ZIO.accessM(_.beam.at(node)(task))

  override def listing[U](key: X#NodeKey[U]): RManaged[Beam[X], Queue[Set[X#Node[U]]]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.listing(key))

  override def node[U](f: Runtime[Beam[X]] => Runtime[U], key: Option[X#NodeKey[U]] = None): RManaged[Beam[X], X#Node[U]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.node(f, key))

  /**
   * Wait till some nodes with given environment will become available.
   */
  def someNodes[U](key: X#NodeKey[U]): RIO[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
    r.beam.listing[U](key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)))
  }

  /**
   * Wait till any node with given environment will become available.
   */
  def anyNode[U](key: X#NodeKey[U]): RIO[Beam[X], X#Node[U]] = someNodes(key).map(_.head)
}