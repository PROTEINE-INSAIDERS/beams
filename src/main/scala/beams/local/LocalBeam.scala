package beams.local

import beams._
import scalaz.zio._

final case class LocalBeam[+Env](node: LocalNode[Env]) extends Beam[LocalNode, Env] {
  override val beam: Beam.Service[LocalNode, Env] = LocalBeam.Service(node)
}

object LocalBeam {

  final case class Service[Env](override val self: LocalNode[Env]) extends Beam.Service[LocalNode, Env] {

    override def forkTo[R, A](node: LocalNode[R])(task: TaskR[Beam[LocalNode, R], A]): Task[Fiber[Throwable, A]] = {
      task.provide(LocalBeam[R](node)).fork
    }

    override def createNode[A](a: A): Task[LocalNode[A]] = ZIO(LocalNode(a))

    override def releaseNode[R](node: LocalNode[R]): Task[Unit] = ZIO.unit

    override def env: Env = self.env
  }

}

