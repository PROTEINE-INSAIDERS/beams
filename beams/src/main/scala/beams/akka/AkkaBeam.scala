package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist.ServiceKey
import beams.Beam
import zio._

import scala.util.control.NonFatal

final case class AkkaBeam(lsp: LocalSpawnProtocol.Ref) extends Beam[AkkaEngine] {
  override def beam: Beam.Service[Any, AkkaEngine] = new Beam.Service[Any, AkkaEngine] {
    private def spawn[T](behavior: Behavior[T]): Task[ActorRef[T]] = Task.effectAsync { cb =>
      try {
        lsp ! LocalSpawnProtocol.Spawn(behavior, cb)
      } catch {
        case NonFatal(e) => cb(Task.fail(e))
      }
    }

    override def runAt[U, A](node: NodeActor.Ref[U])(task: RIO[U, A]): Task[A] =
      for {
        runtime <- ZIO.runtime[Any]
        result <- Task.effectAsyncInterrupt { (cb: Task[A] => Unit) =>
          try {
            val replyTo = runtime.unsafeRun(spawn(ReplyToActor(node, cb)))
            node ! NodeActor.Exec(task, replyTo)
            Left(Task.effectTotal(replyTo ! ReplyToActor.Interrupt))
          } catch {
            case NonFatal(e) => Right(Task.fail(e))
          }
        }
      } yield result

    override def nodeListing[U](key: ServiceKey[NodeActor.Command[U]]): TaskManaged[Queue[Set[NodeActor.Ref[U]]]] =
      Managed.make {
        for {
          queue <- zio.Queue.unbounded[Set[NodeActor.Ref[U]]]
          runtime <- ZIO.runtime[Any]
          listener <- spawn(ReceptionistListener(key, queue, runtime))
        } yield (queue, listener)
      } { case (_, listener) => Task.effectTotal(listener ! ReceptionistListener.Stop)
      }.map { case (queue, _) => queue }
  }
}
