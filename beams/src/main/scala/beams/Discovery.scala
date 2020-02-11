package beams

import zio._

trait Discovery[X <: Backend] {
  def discovery: Discovery.Service[Any, X]
}

object Discovery {

  trait Service[R, X <: Backend] {
    /**
     * Make current node discoverable by given key.
     */
    def announce(key: String): RIO[R, Unit]

    /**
     * List available nodes by given key.
     */
    def listing[U](key: String): RManaged[R, Queue[Set[X#Node[U]]]]

    /**
     * Wait till some nodes with given environment will become available.
     */
    def someNode[U](key: String): RIO[R, Set[X#Node[U]]] = ZIO.accessM { r =>
      listing[U](key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)))
    }

    /**
     * Wait till any node with given environment will become available.
     */
    def anyNode[U](key: String): RIO[R, X#Node[U]] = someNode[U](key).map(_.head)
  }

  trait Syntax[X <: Backend] extends Service[Discovery[X], X] {
    final override def announce(key: String): RIO[Discovery[X], Unit] =
      RIO.accessM(_.discovery.announce(key))

    final override def listing[U](key: String): RManaged[Discovery[X], Queue[Set[X#Node[U]]]] =
      ZManaged.environment[Discovery[X]].flatMap(_.discovery.listing(key))
  }
}

