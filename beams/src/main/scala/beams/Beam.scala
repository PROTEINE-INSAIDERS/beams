package beams

import zio._

trait Engine {
  type Node[+_]
  type NodeKey[_]
}

trait Beam[X <: Engine] {
  def beam: Beam.Service[Any, X]
}

object Beam {

  trait Service[R, X <: Engine] {
    /**
     * List available nodes by given key.
     */
    def nodeListing[U](key: X#NodeKey[U]): RManaged[R, Queue[Set[X#Node[U]]]]

    /**
     * Run task at specified node and waits for result.
     */
    def runAt[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[R, A]
  }

}

trait BeamsSyntax[X <: Engine] extends Beam.Service[Beam[X], X] {
  /**
   * Submit task to specified node and return immediately.
   *
   * This function does not require beam's context and can be used to submit tasks from outside of node (e.g. can be used in bootstrap sequence).
   *
   * Please note that the parent task will not track submitted tasks hence parent task interruption will not interrupt submitted tasks.
   */
  //TODO: нужно всё-таки решить, оставить это в beam, или вынести наружу.
  def submitTo[U](node: X#Node[U])(task: RIO[U, Any]): Task[Unit]

  override def nodeListing[U](key: X#NodeKey[U]): RManaged[Beam[X], Queue[Set[X#Node[U]]]] = ZManaged.environment[Beam[X]].flatMap(_.beam.nodeListing(key))

  override def runAt[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[Beam[X], A] = ZIO.accessM(_.beam.runAt(node)(task))

  /**
   * Wait till some nodes with given environment will become available.
   */
  def someNodes[U](key: X#NodeKey[U]): RIO[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
    r.beam.nodeListing[U](key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)))
  }

  /**
   * Wait till any node with given environment will become available.
   */
  def anyNode[U](key: X#NodeKey[U]): RIO[Beam[X], X#Node[U]] = someNodes(key).map(_.head)
}