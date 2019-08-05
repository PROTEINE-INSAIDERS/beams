package beams

import scalaz.zio._

trait Beam[Node[_], Env] {
  def beam: Beam.Service[Node, Env]
}

object Beam {
  trait Service[Node[_], Env] {
    def forkTo[R, A](node: Node[R])(task: TaskR[Beam[Node, R], A]): Task[Fiber[Throwable, A]]

    def self: Node[Env]

    def spawn[R](a: R): Task[Node[R]]
  }
}