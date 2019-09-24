package beams.akka

import _root_.akka.actor.typed._
import beams._
import scalaz.zio._

final case class AkkaBeam[Env](self: Node[Env], env: Env, timeLimit: TimeLimit, scheduler: Scheduler) extends Beam[Node, Env] {
  override val beam: Beam.Service[Node, Env] = AkkaBeam.Service[Env](self, env)(timeLimit, scheduler)
}

object AkkaBeam {

  final case class Service[+Env](override val self: Node[Env], env: Env)
                                (implicit timeLimit: TimeLimit, scheduler: Scheduler) extends Beam.Service[Node, Env] {
    override def forkTo[R, A](node: Node[R])
                             (task: TaskR[Beam[Node, R], A]): Task[Fiber[Throwable, A]] = {
      /*
      askZio[Exit[Throwable, A]](
        node.ref,
        NodeActor.RunTask(task, _, TimeLimitContainer(timeLimit, node.ref)))
        .flatMap(exit => Task.done(exit))
        .fork
       */
      ???
    }

    override def createNode[Env](a: Env): Task[Node[Env]] = ???
      //TODO: добавить монитор прямо сюда?
      //askZio[NodeActor.Ref[Env]](self.ref, NodeActor.CreateNode(a, _))//.map(AkkaNode[Env])

    override def releaseNode[R](node: Node[R]): Canceler = ZIO.effectTotal(node.ref.tell(NodeActor.Stop))
  }

}
