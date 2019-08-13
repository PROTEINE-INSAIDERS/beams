package beams

import scalaz.zio._

trait Beam[Node[+_], +Env] {
  def beam: Beam.Service[Node, Env]
}

object Beam {
  trait Service[Node[+_], +Env] {
    def forkTo[R, A](node: Node[R])(task: TaskR[Beam[Node, R], A]): Task[Fiber[Throwable, A]]

    def self: Node[Env]

    def createNode[R](r: R): Task[Node[R]]

    def releaseNode[R](node: Node[R]): Canceler

    def env: Env
  }
}

trait BeamSyntax[Node[+_]] {
  def forkTo[R, A](node: Node[R])(task: TaskR[Beam[Node, R], A]): TaskR[Beam[Node, Any], Fiber[Throwable, A]] =
    ZIO.accessM(_.beam.forkTo(node)(task))

  def self[Env]: TaskR[Beam[Node, Env], Node[Env]] = ZIO.access(_.beam.self)

  def createNode[R](r: R): TaskR[Beam[Node, Any], Node[R]] = ZIO.accessM(_.beam.createNode(r))

  def releaseNode[R](node: Node[R]): TaskR[Beam[Node, Any], _] = ZIO.accessM(_.beam.releaseNode(node))

  def env[Env]: TaskR[Beam[Node, Env], Env] = ZIO.access(_.beam.env)

  def node[R](r: R): TaskR[Beam[Node, Any], Managed[Throwable, Node[R]]] = ZIO.access(F =>
    Managed.make(F.beam.createNode(r))(F.beam.releaseNode[R]))
}