package beams.akka

import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

import scala.reflect.runtime.universe._

trait AkkaBeam extends Beam[Node.Ref] {
  protected def nodeActor: Node.Ref[_]

  override val beams: Beam.Service[Any, Node.Ref] = new Beam.Service[Any, Node.Ref] {
    private def onActorContex(f: ActorContext[_] => Unit): Unit = {
      nodeActor ! Node.AccessActorContext { ctx => f(ctx) }
    }

    private def withActorContext[A](f: ActorContext[_] => A): Task[A] = Task.effectAsync { (cb: Task[A] => Unit) =>
      nodeActor ! Node.AccessActorContext { ctx =>
        cb(Task(f(ctx)))
      }
    }

    override def listing[U: TypeTag]: ZManaged[Any, Throwable, Queue[Set[Node.Ref[U]]]] = Managed.make {
      for {
        queue <- scalaz.zio.Queue.unbounded[Set[Node.Ref[U]]]
        runtime <- ZIO.runtime[Any]
        listener <- withActorContext(_.spawnAnonymous(ReceptionistListener(serviceKey[U], queue, runtime)))
      } yield (queue, listener)
    } { case (_, listener) => tellZio(listener, ReceptionistListener.Stop)
    }.map(_._1)

    override def runAt[U, A](node: Node.Ref[U])(task: TaskR[U, A]): TaskR[Any, A] = for {
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
