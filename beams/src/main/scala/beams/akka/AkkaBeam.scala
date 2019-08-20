package beams.akka

import _root_.akka.actor.typed._
import beams._
import scalaz.zio._

final case class AkkaBeam[+Env](self: AkkaNode[Env], env: Env, timeout: TimeLimit, scheduler: Scheduler) extends Beam[AkkaNode, Env] {
  override val beam: Beam.Service[AkkaNode, Env] = AkkaBeam.Service[Env](self, env, timeout, scheduler)
}

object AkkaBeam {

  final case class Service[+Env](
                                  override val self: AkkaNode[Env],
                                  env: Env,
                                  timeout: TimeLimit,
                                  scheduler: Scheduler
                                ) extends Beam.Service[AkkaNode, Env] {
    override def forkTo[R, A](node: AkkaNode[R])
                             (task: TaskR[Beam[AkkaNode, R], A]): Task[Fiber[Throwable, A]] =
      askZio[Exit[Throwable, Any]](
        node.ref,
        NodeActor.RunTask(task.asInstanceOf[TaskR[Beam[AkkaNode, Any], Any]], _, TimeLimitContainer(timeout, node.ref)))(
        timeout,
        scheduler
      ).flatMap(exit => Task.done(exit.asInstanceOf[Exit[Throwable, A]]))
        .fork

    override def createNode[R](a: R): Task[AkkaNode[R]] =
      askZio[NodeActor.Ref](self.ref, NodeActor.CreateNode(a, _))(timeout, scheduler).map(AkkaNode[R])

    override def releaseNode[R](node: AkkaNode[R]): Canceler = ZIO.effectTotal(node.ref.tell(NodeActor.Stop))
  }

}
