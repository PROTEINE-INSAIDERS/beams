package beams.akka

import _root_.akka.actor.typed._
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.util._
import beams._
import scalaz.zio._

final case class AkkaBeam[+Env](self: AkkaNode[Env], env: Env, timeout: LocalTimeout, scheduler: Scheduler) extends Beam[AkkaNode, Env] {
  override val beam: Beam.Service[AkkaNode, Env] = AkkaBeam.Service[Env](self, env, timeout, scheduler)
}

object AkkaBeam {

  final case class Service[+Env](
                                  override val self: AkkaNode[Env],
                                  env: Env,
                                  timeout: LocalTimeout,
                                  scheduler: Scheduler
                                ) extends Beam.Service[AkkaNode, Env] {
    override def forkTo[R, A](node: AkkaNode[R])
                             (task: TaskR[Beam[AkkaNode, R], A]): Task[Fiber[Throwable, A]] = Task.fromFuture { _ =>
      implicit val t: Timeout = timeout.current()
      implicit val s: Scheduler = scheduler

      node.ref
        .ask[Exit[Throwable, Any]](NodeActor.RunTask(task.asInstanceOf[TaskR[Beam[AkkaNode, Any], Any]], _, timeout.migratable()))
    }.flatMap(exit => Task.done(exit.asInstanceOf[Exit[Throwable, A]]))
      .fork

    override def createNode[R](a: R): Task[AkkaNode[R]] = Task.fromFuture { _ =>
      implicit val t: Timeout = timeout.current()
      implicit val s: Scheduler = scheduler
      self.ref.ask[NodeActor.Ref](NodeActor.CreateNode(a, _))
    }.map(AkkaNode[R])

    override def releaseNode[R](node: AkkaNode[R]): Canceler = ZIO.effectTotal(node.ref.tell(NodeActor.Stop))
  }

}
