package beams

import scalaz.zio.clock.Clock
import scalaz.zio._
import org.scalactic._
import org.scalactic.TripleEquals._
import org.scalactic.Tolerance._
import org.scalactic.MapEqualityConstraints._
import org.scalactic.StringNormalizations

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
      * Please note that obtained Queue should not be serialized and passed to other nodes.
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
    override def node[U](f: Beam[X] => U, key: Option[X#Key[U]]): ZManaged[Beam[X], Throwable, X#Node[U]] =
      ZManaged.environment[Beam[X]].flatMap(_.beam.node(f, key))

    override def nodes[U](key: X#Key[U]): ZManaged[Beam[X], Throwable, Queue[Set[X#Node[U]]]] =
      ZManaged.environment[Beam[X]].flatMap(_.beam.nodes(key))

    override def runAt[U, A](node: X#Node[U])(task: TaskR[U, A]): TaskR[Beam[X], A] =
      TaskR.accessM(_.beam.runAt(node)(task))

    override def submitTo[U](node: X#Node[U])(task: TaskR[U, Any]): TaskR[Beam[X], Unit] =
      TaskR.accessM(_.beam.submitTo(node)(task))

    /**
      * Wait till some nodes will become available.
      */
    def someNodes[U](key: X#Key[U]): TaskR[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
      r.beam.nodes(key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)).provide(Clock.Live))
    }

    /**
      * Wait till any node will become available.
      */
    def anyNode[U](key: X#Key[U]): TaskR[Beam[X], X#Node[U]] = someNodes(key).map(_.head)
  }
}

object T {
  case class Test(d: Double) {
    override def equals(obj: Any): Boolean = obj match {
      case d: Double => d === (d +- 0.1)
      case other => super.equals(other)
    }
  }

  def aaa[K] = new Uniformity[Map[K, Double]] {
    override def normalizedOrSame(b: Any): Any = ???

    override def normalizedCanHandle(b: Any): Boolean = ???

    override def normalized(a: Map[K, Double]): Map[K, Double] = ???
  }

  def main(args: Array[String]): Unit = {
    val a = Array.ofDim(3)
   //println(Map(1 -> Test(1d)) === Map(1 -> 1.1d))
  }
}