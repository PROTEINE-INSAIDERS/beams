package beams.backend.akka

import akka.actor.typed._
import beams._
import zio._
import zio.clock._
import zio.console._
import zio.duration._


private[akka] final case class AkkaBeam(
                                         nodeActor: NodeActor.Ref[Any],
                                         ctx: ActorSystem[_]
                                       )
  extends Deathwatch[AkkaBackend]
    with Discovery[AkkaBackend]
    with Exclusive[AkkaBackend]
    with Execution[AkkaBackend]
    with NodeFactory[AkkaBackend] {

  private def spawn[T](behavior: Behavior[T]): Task[ActorRef[T]] = guardAsync { cb =>
    nodeActor ! NodeActor.Spawn(behavior, cb)
  }

  override def deathwatch: Deathwatch.Service[Any, AkkaBackend] = new Deathwatch.Service[Any, AkkaBackend] {
    override def deathwatch(node: NodeActor.Ref[Any]): RIO[Any, Unit] = for {
      runtime <- ZIO.runtime[Any]
      _ <- guardAsyncInterrupt { (cb: Task[Unit] => Unit) =>
        val deathwatch = runtime.unsafeRun(spawn(DeathWatch(node, cb)))
        Left(Task.effectTotal(deathwatch ! DeathWatch.Stop))
      }
    } yield ()
  }

  override def discovery: Discovery.Service[Any, AkkaBackend] = new Discovery.Service[Any, AkkaBackend] {
    /**
      * Make current node discoverable by given key.
      */
    override def announce(key: String): RIO[Any, Unit] = guardAsync { cb =>
      nodeActor ! NodeActor.Register(key, cb)
    }

    /**
      * List available nodes by given key.
      */
    override def listing[U](key: String): RManaged[Any, Queue[Set[NodeActor.Ref[U]]]] =
      Managed.make {
        for {
          queue <- zio.Queue.unbounded[Set[NodeActor.Ref[U]]]
          runtime <- ZIO.runtime[Any]
          listener <- spawn(ReceptionistListener(NodeActor.Key[U](key), queue, runtime))
        } yield (queue, listener)
      } { case (_, listener) => Task.effectTotal(listener ! ReceptionistListener.Stop)
      }.map { case (queue, _) => queue }
  }

  override def execution: Execution.Service[Any, AkkaBackend] = new Execution.Service[Any, AkkaBackend] {
    override def at[U, A](node: NodeActor.Ref[U])(task: RIO[U, A]): Task[A] = for {
      runtime <- ZIO.runtime[Any]
      result <- guardAsyncInterrupt { (cb: Task[A] => Unit) =>
        val replyTo = runtime.unsafeRun(spawn(TaskReplyToActor(node, cb)))
        node ! NodeActor.Exec(task, replyTo)
        Left(Task.effectTotal(replyTo ! TaskReplyToActor.Interrupt))
      }
    } yield result
  }

  override def nodeFactory: NodeFactory.Service[Any, AkkaBackend] = new NodeFactory.Service[Any, AkkaBackend] {
    override def node[U](f: Runtime[Beam[AkkaBackend]] => Runtime[U]): TaskManaged[NodeActor.Ref[U]] =
      Managed.make(spawn(NodeActor(f))) { node => Task.effectTotal(node ! NodeActor.Stop) }
  }

  override def exclusive: Exclusive.Service[Any, AkkaBackend] = new Exclusive.Service[Any, AkkaBackend] {
    override def exclusive[A, R1 <: Any](key: String)(task: RIO[R1, A]): RIO[R1, Option[A]] = for {
      exclusivePromise <- Promise.make[Throwable, Unit]
      exclusiveFiber <- (exclusivePromise.await *> task).fork // а нужен ли фибер???
      result <- guardAsync { (cb: Task[Option[ExclusiveActor.Ref]] => Unit) =>
        nodeActor ! NodeActor.Exclusive(key, cb)
      }.bracket {
        case Some(exclusiveActor) => ???
        case None => Task.unit
      } {
        case Some(_) => exclusiveFiber.join.map(Some(_))
        case None => exclusiveFiber.interrupt *> Task.succeed(None)
      }
    } yield result
  }
}
