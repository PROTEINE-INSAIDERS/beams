package beams.akka

import _root_.akka.actor.typed._
import beams._
import scalaz.zio._

final case class AkkaBeam[Env](self: AkkaNode[Env], env: Env, timeLimit: TimeLimit, scheduler: Scheduler) extends Beam[AkkaNode, Env] {
  override val beam: Beam.Service[AkkaNode, Env] = AkkaBeam.Service[Env](self, env)(timeLimit, scheduler)
}

object AkkaBeam {

  final case class Service[+Env](override val self: AkkaNode[Env], env: Env)
                                (implicit timeLimit: TimeLimit, scheduler: Scheduler) extends Beam.Service[AkkaNode, Env] {
    override def forkTo[R, A](node: AkkaNode[R])
                             (task: TaskR[Beam[AkkaNode, R], A]): Task[Fiber[Throwable, A]] =
      askZio[Exit[Throwable, A]](
        node.ref,
        NodeActor.RunTask(task, _, TimeLimitContainer(timeLimit, node.ref)))
        .flatMap(exit => Task.done(exit))
        .fork

    override def createNode[Env](a: Env): Task[AkkaNode[Env]] =
      //TODO: добавить монитор прямо сюда?
      askZio[NodeActor.Ref[Env]](self.ref, NodeActor.CreateNode(a, _)).map(AkkaNode[Env])

    override def releaseNode[R](node: AkkaNode[R]): Canceler = ZIO.effectTotal(node.ref.tell(NodeActor.Stop))
  }

}
