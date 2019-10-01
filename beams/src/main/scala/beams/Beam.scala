package beams

import scalaz.zio.clock.Clock
import scalaz.zio.{Queue, Schedule, TaskR, ZIO, ZManaged}

trait Beam[X <: Backend] {
  def beam: Beam.Service[Any, X]
}

object Beam {

  trait Service[R, X <: Backend] {
    /**
      * Create node and optionally register it.
      */
    def node[U](f: Beam[X] => U, key: Option[X#Key[U]] = None): ZManaged[R, Throwable, X#Node[U]]

    /**
      * List registered nodes by key.
      */
    def nodes[U](key: X#Key[U]): ZManaged[R, Throwable, Queue[Set[X#Node[U]]]]

    /**
      * Run task at node and wait for result.
      */
    def runAt[U, A](node: X#Node[U])(task: TaskR[U, A]): TaskR[R, A]

    /**
      * Submit task to node and return immediately.
      */
    def submitTo[U](node: X#Node[U])(task: TaskR[U, Any]): TaskR[R, Unit]
  }

  trait Syntax[X <: Backend] extends Beam.Service[Beam[X], X] {
    override def node[U](f: Beam[X] => U, k: Option[X#Key[U]]): ZManaged[Beam[X], Throwable, X#Node[U]] = ZManaged.environment[Beam[X]].flatMap(_.beam.node(f, k))

    override def nodes[U](k: X#Key[U]): ZManaged[Beam[X], Throwable, Queue[Set[X#Node[U]]]] = ZManaged.environment[Beam[X]].flatMap(_.beam.nodes(k))

    override def runAt[U, A](n: X#Node[U])(t: TaskR[U, A]): TaskR[Beam[X], A] = TaskR.accessM(_.beam.runAt(n)(t))

    override def submitTo[U](n: X#Node[U])(t: TaskR[U, Any]): TaskR[Beam[X], Unit] = TaskR.accessM(_.beam.submitTo(n)(t))

    /**
      * Wait till some nodes will become available.
      */
    def someNodes[U](k: X#Key[U]): TaskR[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
      r.beam.nodes(k).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)).provide(Clock.Live))
    }

    /**
      * Wait till any node will become available.
      */
    def anyNode[U](k: X#Key[U]): TaskR[Beam[X], X#Node[U]] = someNodes(k).map(_.head)
  }
}

