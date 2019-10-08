package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import scalaz.zio._
import scalaz.zio.internal.PlatformLive

import scala.util.control.NonFatal

private[akka] object NodeProtocol {
  type Ref[+R] = ActorRef[Command[R]]

  type Key[R] = ServiceKey[Command[R]]

  sealed trait Command[-R]

  final case class Node[U](
                            f: AkkaBeam[U] => U,
                            key: Option[NodeProtocol.Key[U]],
                            cb: Task[NodeProtocol.Ref[U]] => Unit
                          ) extends Command[Any] with NonSerializableMessage

  final case class Nodes[U](
                             key: NodeProtocol.Key[U],
                             queue: Queue[Set[NodeProtocol.Ref[U]]],
                             cb: Task[ReceptionistListener.Ref] => Unit
                           ) extends Command[Any] with NonSerializableMessage

  final case class RunAt[U, +A](
                                 node: NodeProtocol.Ref[U],
                                 task: TaskR[U, A],
                                 cb: Task[HomunculusLoxodontus.Ref[A]] => Unit
                               ) extends Command[Any] with NonSerializableMessage

  final case class Submit[R](task: TaskR[R, Any]) extends Command[R] with SerializableMessage

  object Shutdown extends Command[Any]

  def apply[R](f: AkkaBeam[R] => R, key: Option[NodeProtocol.Key[R]]): Behavior[Command[R]] = Behaviors.setup { ctx =>
    val runtime = Runtime(f(new AkkaBeam(ctx.self)), PlatformLive.fromExecutionContext(ctx.executionContext))
    key.foreach { k => ctx.system.receptionist.tell(Receptionist.Register(k, ctx.self)) }
    Behaviors.receiveMessagePartial {
      case Node(f, key, cb) =>
        try {
          cb(IO.succeed(ctx.spawnAnonymous(NodeProtocol(f, key))))
        } catch {
          case NonFatal(e) => cb(IO.fail(e))
        }
        Behaviors.same
      case Nodes(key, queue, cb) =>
        try {
          cb(IO.succeed(ctx.spawnAnonymous(ReceptionistListener(key, queue, runtime))))
        } catch {
          case NonFatal(e) => cb(IO.fail(e))
        }
        Behaviors.same
      case RunAt(node, task, cb) =>
        // 1. не блокироваться в текущем контексте.
        // 2. создать ожидающий актор.
        ???
      case Submit(task) =>
        ctx.spawnAnonymous(TaskProtocol[R, Any](task, runtime, _ => ()))
        Behaviors.same
      case Shutdown =>

        //TODO: нужно заглушить все дочерние задачи, все дочерние ноды и все дочерние ожидающие акторы.
        // так как ожидающие акторы привязаны задачам, которые крутятся в текущей момент на ноде, их можно
        // отдельно не глушить.
        Behaviors.stopped
    }
  }
}
