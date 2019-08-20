package beams

import scalaz.zio._

trait Beam[Node[+ _], +Env] {
  def beam: Beam.Service[Node, Env]
}

object Beam {

  trait Service[Node[+ _], +Env] {
    def forkTo[R, A](node: Node[R])(task: TaskR[Beam[Node, R], A]): Task[Fiber[Throwable, A]]

    def self: Node[Env]

    // это не верное определение кластера, потому что env корневых узлов можен не совпадать с Env текущего узла.
    // def cluster: Task[List[Node[Env]]]

    def createNode[R](r: R): Task[Node[R]]

    def releaseNode[R](node: Node[R]): Canceler

    def env: Env
  }

}
