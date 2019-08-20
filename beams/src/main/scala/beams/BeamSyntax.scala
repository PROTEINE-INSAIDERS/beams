package beams

import scalaz.zio._

trait BeamSyntax[Node[+ _]] {
  def forkTo[R, A](node: Node[R])(task: TaskR[Beam[Node, R], A]): TaskR[Beam[Node, Any], Fiber[Throwable, A]] =
    ZIO.accessM(_.beam.forkTo(node)(task))

  def self[Env]: TaskR[Beam[Node, Env], Node[Env]] = ZIO.access(_.beam.self)

//  def cluster[Env](): TaskR[Beam[Node, Env], List[Node[Env]]] = ZIO.accessM(_.beam.cluster)

  def createNode[R](r: R): TaskR[Beam[Node, Any], Node[R]] = ZIO.accessM(_.beam.createNode(r))

  def releaseNode[R](node: Node[R]): TaskR[Beam[Node, Any], _] = ZIO.accessM(_.beam.releaseNode(node))

  def env[Env]: TaskR[Beam[Node, Env], Env] = ZIO.access(_.beam.env)

  def node[R](r: R): TaskR[Beam[Node, Any], Managed[Throwable, Node[R]]] = ZIO.access(F =>
    Managed.make(F.beam.createNode(r))(F.beam.releaseNode[R]))
}

object BeamSyntax {
  def apply[Node[+ _]](): BeamSyntax[Node] = new BeamSyntax[Node]() {}
}