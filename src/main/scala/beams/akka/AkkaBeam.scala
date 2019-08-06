package beams.akka

import beams.Beam
import scalaz.zio.{Fiber, Task, TaskR}

final case class AkkaBeam[+Env]() extends Beam[AkkaNode, Env]  {
  override val beam: Beam.Service[AkkaNode, Env] = AkkaBeam.Service[Env]()
}

object AkkaBeam {
  final case class Service[+Env]() extends Beam.Service[AkkaNode, Env] {
    override def forkTo[R, A](node: AkkaNode[R])(task: TaskR[Beam[AkkaNode, R], A]): Task[Fiber[Throwable, A]] = ???

    override def self: AkkaNode[Env] = ???

    override def createNode[R](a: R): Task[AkkaNode[R]] = ???

    override def releaseNode[R](node: AkkaNode[R]): Task[Unit] = ???

    override def env: Env = ???
  }
}