package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import beams._
import zio._

import scala.reflect.ClassTag
import scala.util.control.NonFatal

private[akka] final case class AkkaBeam(nodeActor: NodeActor.Ref[Any]) extends Beam[AkkaBackend] {
  override def beam: Beam.Service[Any, AkkaBackend] = new Beam.Service[Any, AkkaBackend] {
    private def spawn[T](behavior: Behavior[T], key: Option[ServiceKey[T]]): Task[ActorRef[T]] = Task.effectAsync { cb =>
      try {
        nodeActor ! NodeActor.Spawn(behavior, None, cb)
      } catch {
        case NonFatal(e) => cb(Task.fail(e))
      }
    }

    override def at[U, A](node: NodeActor.Ref[U])(task: RIO[U, A]): Task[A] =
      for {
        runtime <- ZIO.runtime[Any]
        result <- Task.effectAsyncInterrupt { (cb: Task[A] => Unit) =>
          try {
            val replyTo = runtime.unsafeRun(spawn(ReplyToActor(node, cb), None))
            node ! NodeActor.Exec(task, replyTo)
            Left(Task.effectTotal(replyTo ! ReplyToActor.Interrupt))
          } catch {
            case NonFatal(e) => Right(Task.fail(e))
          }
        }
      } yield result

    override def key[U: ClassTag](id: String): RIO[Any, ServiceKey[NodeActor.Command[U]]] = IO {
      ServiceKey[NodeActor.Command[U]](id)
    }

    override def listing[U](key: ServiceKey[NodeActor.Command[U]]): TaskManaged[Queue[Set[NodeActor.Ref[U]]]] =
      Managed.make {
        for {
          queue <- zio.Queue.unbounded[Set[NodeActor.Ref[U]]]
          runtime <- ZIO.runtime[Any]
          listener <- spawn(ReceptionistListener(key, queue, runtime), None)
        } yield (queue, listener)
      } { case (_, listener) => Task.effectTotal(listener ! ReceptionistListener.Stop)
      }.map { case (queue, _) => queue }

    override def node[U](f: Runtime[Beam[AkkaBackend]] => Runtime[U], key: Option[NodeActor.Key[U]]): TaskManaged[NodeActor.Ref[U]] =
      Managed.make(spawn(NodeActor(f), key)) { node => Task.effectTotal(node ! NodeActor.Stop) }

  }
}
