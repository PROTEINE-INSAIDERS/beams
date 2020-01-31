package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.receptionist._
import beams._
import zio._

import scala.reflect.ClassTag

private[akka] final case class AkkaBeam(nodeActor: NodeActor.Ref[Any]) extends Beam[AkkaBackend] {
  override def beam: Beam.Service[Any, AkkaBackend] = new Beam.Service[Any, AkkaBackend] {
    private def spawn[T](behavior: Behavior[T]): Task[ActorRef[T]] = guardAsync { cb =>
      nodeActor ! NodeActor.Spawn(behavior, cb)
    }

    override def atNode[U, A](node: NodeActor.Ref[U])(task: RIO[U, A]): Task[A] = for {
      runtime <- ZIO.runtime[Any]
      result <- guardAsyncInterrupt { (cb: Task[A] => Unit) =>
        val replyTo = runtime.unsafeRun(spawn(ReplyToActor(node, cb)))
        node ! NodeActor.Exec(task, replyTo)
        Left(Task.effectTotal(replyTo ! ReplyToActor.Interrupt))
      }
    } yield result

    override def deathwatchNode(node: NodeActor.Ref[Any]): RIO[Any, Unit] = for {
      runtime <- ZIO.runtime[Any]
      _ <- guardAsyncInterrupt { (cb: Task[Unit] => Unit) =>
        val deathwatch = runtime.unsafeRun(spawn(DeathWatch(node, cb)))
        Left(Task.effectTotal(deathwatch ! DeathWatch.Stop))
      }
    } yield ()

    override def listNodes[U](key: String): TaskManaged[Queue[Set[NodeActor.Ref[U]]]] =
      Managed.make {
        for {
          queue <- zio.Queue.unbounded[Set[NodeActor.Ref[U]]]
          runtime <- ZIO.runtime[Any]
          listener <- spawn(ReceptionistListener(NodeActor.Key[U](key), queue, runtime))
        } yield (queue, listener)
      } { case (_, listener) => Task.effectTotal(listener ! ReceptionistListener.Stop)
      }.map { case (queue, _) => queue }

    override def createNode[U](f: Runtime[Beam[AkkaBackend]] => Runtime[U]): TaskManaged[NodeActor.Ref[U]] =
      Managed.make(spawn(NodeActor(f))) { node => Task.effectTotal(node ! NodeActor.Stop) }

    override def announceNode(key: String): Task[Unit] = guardAsync { cb =>
      nodeActor ! NodeActor.Register(key, cb)
    }
  }
}
