import scalaz.zio._

package object beams {
  def forkTo[Node[_], Env, R, A](node: Node[R])(task: TaskR[Beam[Node, R], A]): TaskR[Beam[Node, Env], Fiber[Throwable, A]] =
    ZIO.accessM(_.beam.forkTo(node)(task))

  def self[Node[_], Env]: TaskR[Beam[Node, Env], Node[Env]] = ZIO.access(_.beam.self)

  def spawn[Node[_], Env, R](r: R): TaskR[Beam[Node, Env], Node[R]] = ZIO.accessM(_.beam.spawn(r))
}
