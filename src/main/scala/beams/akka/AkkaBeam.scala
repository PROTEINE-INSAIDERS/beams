package beams.akka

import _root_.akka.actor.typed.scaladsl.AskPattern._
import beams._
import scalaz.zio._

final case class AkkaBeam[+Env](ctx: NodeActor.Ctx, ref: NodeActor.Ref, env: Env) extends Beam[AkkaNode, Env] {
  override val beam: Beam.Service[AkkaNode, Env] = AkkaBeam.Service[Env](ctx, ref, env)
}

object AkkaBeam {

  final case class Service[+Env](ctx: NodeActor.Ctx, ref: NodeActor.Ref, env: Env) extends Beam.Service[AkkaNode, Env] {
    override def forkTo[R, A](node: AkkaNode[R])(task: TaskR[Beam[AkkaNode, R], A]): Task[Fiber[Throwable, A]] = ???

    override def createNode[R](a: R): Task[AkkaNode[R]] = Task.fromFuture { _ =>
      implicit val scheduler = ctx.system.scheduler
      // TODO хочется иметь таймаут для всей программы, не для каждого локального запроса.
      ref.ask(replyTo => ???)
      ???
    }

    override def releaseNode[R](node: AkkaNode[R]): Task[Unit] = Task(node.ref.tell(NodeActor.Stop))

    override def self: AkkaNode[Env] = AkkaNode(ref)
  }

}