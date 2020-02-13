package beams.backend.akka

import akka.actor.typed.receptionist.Receptionist.Registered
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl._
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import beams._
import zio._

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

private[akka] final case class AkkaBeam[R](
                                            implicit actorContext: ActorContext[NodeActor.Command[R]],
                                            executionContext: ExecutionContext
                                          )
  extends Deathwatch[AkkaBackend]
    with Discovery[AkkaBackend]
    with Exclusive[AkkaBackend]
    with Execution[AkkaBackend]
    with NodeFactory[AkkaBackend] {

  override def deathwatch: Deathwatch.Service[Any, AkkaBackend] = new Deathwatch.Service[Any, AkkaBackend] {
    override def deathwatch(node: NodeActor.Ref[Any]): RIO[Any, Unit] = guardAsyncInterrupt { (cb: Task[Unit] => Unit) =>
      val deathwatch = actorContext.spawnAnonymous(DeathWatch(node, cb))
      Left(Task.effectTotal(deathwatch ! DeathWatch.Stop))
    }.on(executionContext)
  }

  override def discovery: Discovery.Service[Any, AkkaBackend] = new Discovery.Service[Any, AkkaBackend] {
    /**
      * Make current node discoverable by given key.
      */
    override def announce(key: String): RIO[Any, Unit] =
      actorContext.system.receptionist.ask[Registered](Receptionist.Register(ServiceKey[NodeActor.Command[R]](key), actorContext.self, _)) *>
        Task.unit

    /**
      * List available nodes by given key.
      */
    override def listing[U](key: String): RManaged[Any, Queue[Set[NodeActor.Ref[U]]]] =
      Managed.make {
        for {
          queue <- zio.Queue.unbounded[Set[NodeActor.Ref[U]]]
          runtime <- ZIO.runtime[Any]
          listener <- IO(actorContext.spawnAnonymous(ReceptionistListener(NodeActor.Key[U](key), queue, runtime))).on(executionContext)
        } yield (queue, listener)
      } { case (_, listener) => Task.effectTotal(listener ! ReceptionistListener.Stop)
      }.map { case (queue, _) => queue }
  }

  override def execution: Execution.Service[Any, AkkaBackend] = new Execution.Service[Any, AkkaBackend] {
    override def at[U, A](node: NodeActor.Ref[U])(task: RIO[U, A]): Task[A] = guardAsyncInterrupt { (cb: Task[A] => Unit) =>
      val replyTo = actorContext.spawnAnonymous(TaskReplyToActor(node, cb))
      try {
        node ! NodeActor.Exec(task, replyTo)
      } catch {
        case NonFatal(e) =>
          replyTo ! TaskReplyToActor.Stop
          throw e
      }
      Left(Task.effectTotal(replyTo ! TaskReplyToActor.Interrupt))
    }.on(executionContext)
  }

  override def nodeFactory: NodeFactory.Service[Any, AkkaBackend] = new NodeFactory.Service[Any, AkkaBackend] {
    override def node[U](f: Runtime[Beam[AkkaBackend]] => Runtime[U]): TaskManaged[NodeActor.Ref[U]] =
      Managed.make(IO(actorContext.spawnAnonymous(NodeActor(f))).on(executionContext)) { node => Task.effectTotal(node ! NodeActor.Stop) }
  }

  override def exclusive: Exclusive.Service[Any, AkkaBackend] = new Exclusive.Service[Any, AkkaBackend] {
    override def exclusive[A, R1 <: Any](key: String)(task: RIO[R1, A]): RIO[R1, Option[A]] = for {
      clusterSingleton <- IO(ClusterSingleton(actorContext.system))
      exclusive <- IO(clusterSingleton.init(SingletonActor(ExclusiveActor(), "beams-exclusive")))
      result <- exclusive.ask[Boolean](ExclusiveActor.Register(key, _)).bracket { registered =>
        if (registered) IO.effectTotal(exclusive ! ExclusiveActor.Unregister(key)) else Task.unit
      } { registered =>
        if (registered) task.map(Some(_)) else Task.none
      }
    } yield result
  }
}
