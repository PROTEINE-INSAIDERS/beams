package beams.akka

import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

import scala.reflect.runtime.universe

trait AkkaBeam extends Beam[BeamsSupervisor.Ref] {
  protected def nodeActor: BeamsSupervisor.Ref[_]

  override val beams: Beam.Service[Any, BeamsSupervisor.Ref] = new Beam.Service[Any, BeamsSupervisor.Ref] {
    private def onActorContex(f: ActorContext[_] => Unit): Unit = {
      nodeActor ! BeamsSupervisor.AccessActorContext { ctx => f(ctx) }
    }

    private def withActorContext[A](f: ActorContext[_] => A): Task[A] = Task.effectAsync { (cb: Task[A] => Unit) =>
      nodeActor ! BeamsSupervisor.AccessActorContext { ctx =>
        cb(Task.succeed(f(ctx)))
      }
    }

    override def listing[U: universe.TypeTag]: ZManaged[Any, Throwable, Queue[Set[BeamsSupervisor.Ref[U]]]] = Managed.make {
      for {
        queue <- scalaz.zio.Queue.unbounded[Set[BeamsSupervisor.Ref[U]]]
        runtime <- ZIO.runtime[Any]
        listener <- withActorContext(_.spawnAnonymous(ReceptionistListener(serviceKey[U], queue, runtime)))
      } yield (queue, listener)
    } { case (_, listener) => tellZio(listener, ReceptionistListener.Stop)
    }.map(_._1)

    override def forkTo[U, A](node: BeamsSupervisor.Ref[U])(task: TaskR[U, A]): TaskR[Any, Fiber[Throwable, A]] = for {
      result <- Task.effectAsyncInterrupt { (cb: Task[A] => Unit) =>
        onActorContex { ctx =>
          ctx.spawnAnonymous {
            HomunculusLoxodontus[BeamsSupervisor.Command[U], Exit[Throwable, A]](node, replyTo => BeamsSupervisor.Exec(task, replyTo), r => cb(ZIO.done(r)))
          }
          ()
        }
        //TODO: implement interruption
        Left(Task.unit)
      }.fork
    } yield result
  }
}
