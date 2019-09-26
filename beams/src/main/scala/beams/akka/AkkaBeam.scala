package beams.akka

import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

import scala.reflect.runtime.universe._

trait AkkaBeam extends Beam[NodeActor.Ref] {
  protected def nodeActor: NodeActor.Ref[_]

  override val beams: Beam.Service[Any, NodeActor.Ref] = new Beam.Service[Any, NodeActor.Ref] {
    private def onActorContex(f: ActorContext[_] => Unit): Unit = {
      nodeActor ! NodeActor.AccessActorContext { ctx => f(ctx) }
    }

    private def withActorContext[A](f: ActorContext[_] => A): Task[A] = Task.effectAsync { (cb: Task[A] => Unit) =>
      nodeActor ! NodeActor.AccessActorContext { ctx =>
        cb(Task(f(ctx)))
      }
    }

    override def nodeListing[U: TypeTag]: ZManaged[Any, Throwable, Queue[Set[NodeActor.Ref[U]]]] = Managed.make {
      for {
        queue <- scalaz.zio.Queue.unbounded[Set[NodeActor.Ref[U]]]
        runtime <- ZIO.runtime[Any]
        listener <- withActorContext(_.spawnAnonymous(ReceptionistListener(nodeKey[U], queue, runtime)))
      } yield (queue, listener)
    } { case (_, listener) => tellZIO(listener, ReceptionistListener.Stop)
    }.map { case (queue, _) => queue }

    override def runAt[U, A](node: NodeActor.Ref[U])(task: TaskR[U, A]): TaskR[Any, A] = for {
      promise <- Promise.make[Nothing, HomunculusLoxodontus.Ref]
      runtime <- ZIO.runtime[Any]
      result <- Task.effectAsyncInterrupt { (cb: Task[A] => Unit) =>
        onActorContex { ctx =>
          //TODO: more safety??
          val homunculusLoxodontus = ctx.spawnAnonymous(HomunculusLoxodontus(node, task, cb))
          runtime.unsafeRun(promise.succeed(homunculusLoxodontus))
          ()
        }
        Left(promise.await.flatMap(homunculusLoxodontus => tellZIO(homunculusLoxodontus, HomunculusLoxodontus.Interrupt)))
      }
    } yield result
  }
}
