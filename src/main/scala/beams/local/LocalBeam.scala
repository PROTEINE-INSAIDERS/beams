package beams.local

import beams.Beam
import scalaz.zio._

final case class LocalNode[Env](env: Env) 

final case class LocalBeam[Env](node: LocalNode[Env]) extends Beam[LocalNode, Env] {
  override def beam: Beam.Service[LocalNode, Env] = LocalBeam.Service(node)
}

object LocalBeam {
  final case class Service[Env](node: LocalNode[Env]) extends Beam.Service[LocalNode, Env] {

    override def forkTo[R, A](node: LocalNode[R])(task: TaskR[Beam[LocalNode, R], A]): Task[Fiber[Throwable, A]] = {
      task.provide(LocalBeam[R](node)).fork
    }

    override def spawn[A](a: A): Task[LocalNode[A]] = ZIO(LocalNode(a))

    override def self: LocalNode[Env] = ???
  }
}

