package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams.backend.akka.ReplyToActor.{Terminated, _}
import zio._

import scala.reflect._

private abstract class ReplyToActor[A](actor: ActorRef[Nothing]) {
  protected final def actorTerminatedException: ActorTerminatedException =
    ActorTerminatedException(s"Actor $actor terminated before sending response.", actor)

  protected final val behavior: Behavior[Any] = Behaviors.setup[Any] { ctx =>
    guardBehavior1(error) {
      ctx.watchWith(actor, Terminated)
      Behaviors.receiveMessagePartial {
        case Stop =>
          Behaviors.stopped
        case Terminated =>
          terminated()
          Behaviors.stopped
        case a =>
          done(a.asInstanceOf[A])
          ctx.unwatch(actor)
          Behaviors.stopped
      }
    }
  }

  protected def done(a: A)

  protected def error(e: Throwable): Unit

  protected def terminated()
}

private object ReplyToActor {

  sealed trait Command[+A]

  object Stop extends Command[Nothing] with SerializableMessage

  private object Terminated extends Command[Nothing] with SerializableMessage

  def apply[A: ClassTag](actor: ActorRef[Nothing], cb: Task[A] => Unit): Behavior[Any] = new ReplyToActor[A](actor) {
    final override protected def done(a: A): Unit = cb(Task.succeed(a))

    final override protected def error(e: Throwable): Unit = cb(Task.fail(e))

    final override protected def terminated(): Unit = cb(Task.fail(actorTerminatedException))
  }.behavior
}
