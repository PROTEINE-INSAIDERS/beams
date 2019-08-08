package beams.akka

import akka.actor.typed._
import beams.Beam
import scalaz.zio._

final case class AkkaBeam[+Env](node: AkkaNode[Env]) extends Beam[AkkaNode, Env] {
  override val beam: Beam.Service[AkkaNode, Env] = AkkaBeam.Service[Env](node)
}

object AkkaBeam {
  final case class Service[+Env](override val self: AkkaNode[Env]) extends Beam.Service[AkkaNode, Env] {
    override def forkTo[R, A](node: AkkaNode[R])(task: TaskR[Beam[AkkaNode, R], A]): Task[Fiber[Throwable, A]] = ???

    override def createNode[R](a: R): Task[AkkaNode[R]] = ???

    override def releaseNode[R](node: AkkaNode[R]): Task[Unit] = Task(node.ref.tell(NodeActor.Stop))

    override def env: Env = self.env
  }
}