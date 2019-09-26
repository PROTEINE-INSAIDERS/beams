package beams.akka

import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

import scala.reflect.runtime.universe._

trait AkkaBeam extends Beam[AkkaNode.Ref] {
  protected def nodeActor: AkkaNode.Ref[_]

  override val beams: Beam.Service[Any, AkkaNode.Ref] = new Beam.Service[Any, AkkaNode.Ref] {
    private def onActorContex(f: ActorContext[_] => Unit): Unit = {
      nodeActor ! AkkaNode.AccessActorContext { ctx => f(ctx) }
    }

    private def withActorContext[A](f: ActorContext[_] => A): Task[A] = Task.effectAsync { (cb: Task[A] => Unit) =>
      nodeActor ! AkkaNode.AccessActorContext { ctx =>
        cb(Task(f(ctx)))
      }
    }

    override def nodeListing[U: TypeTag]: ZManaged[Any, Throwable, Queue[Set[AkkaNode.Ref[U]]]] = Managed.make {
      for {
        queue <- scalaz.zio.Queue.unbounded[Set[AkkaNode.Ref[U]]]
        runtime <- ZIO.runtime[Any]
        listener <- withActorContext(_.spawnAnonymous(ReceptionistListener(serviceKey[U], queue, runtime)))
      } yield (queue, listener)
    } { case (_, listener) => tellZio(listener, ReceptionistListener.Stop)
    }.map { case (queue, _) => queue }

    override def runAt[U, A](node: AkkaNode.Ref[U])(task: TaskR[U, A]): TaskR[Any, A] = for {
      promise <- Promise.make[Nothing, HomunculusLoxodontus.Ref]
      runtime <- ZIO.runtime[Any]
      result <- Task.effectAsyncInterrupt { (cb: Task[A] => Unit) =>
        onActorContex { ctx =>
          //TODO: more safety??
          val homunculusLoxodontus = ctx.spawnAnonymous(HomunculusLoxodontus(node, task, cb))
          runtime.unsafeRun(promise.succeed(homunculusLoxodontus))
          ()
        }
        Left(promise.await.flatMap(homunculusLoxodontus => tellZio(homunculusLoxodontus, HomunculusLoxodontus.Cancel)))
      }
    } yield result
  }
}
