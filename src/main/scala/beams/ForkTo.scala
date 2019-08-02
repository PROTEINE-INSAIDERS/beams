package beams

import scalaz.zio._

trait ForkTo {
  type Node

  def forkTo: ForkTo.Service[Node]
}

object ForkTo {
  trait Service[Node] {
    def forkTo[R, E, A](node: Node)(a: ZIO[R, E, A]): ZIO[R, Nothing, Fiber[E, A]]
  }
}
