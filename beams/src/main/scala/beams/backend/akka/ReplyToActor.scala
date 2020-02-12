package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams.backend.akka.ReplyToActor.{Terminated, _}
import zio._

import scala.reflect._

private abstract class ReplyToActor[A: ClassTag](actor: ActorRef[Nothing]) {
  protected final def actorTerminatedException: ActorTerminatedException =
    ActorTerminatedException(s"Actor $actor terminated before sending response.", actor)

  protected final val behavior: Behavior[A] = Behaviors.setup[Command[A]] { ctx =>
    guardBehavior1(error) {
      ctx.watchWith(actor, Terminated)
      Behaviors.receiveMessagePartial {
        case Done(a) =>
          done(a)
          ctx.unwatch(actor)
          Behaviors.stopped
        case Terminated =>
          terminated()
          Behaviors.stopped
      }
    }
  }.transformMessages { case a => Done(a) }

  protected def done(a: A)

  protected def error(e: Throwable): Unit

  protected def terminated()
}

private object ReplyToActor {

  private sealed trait Command[+A]

  private final case class Done[A](a: A) extends Command[A] with SerializableMessage

  private object Terminated extends Command[Nothing] with SerializableMessage

  def apply[A: ClassTag](actor: ActorRef[Nothing], cb: Task[A] => Unit): Behavior[A] = new ReplyToActor[A](actor) {
    final override protected def done(a: A): Unit = cb(Task.succeed(a))

    final override protected def error(e: Throwable): Unit = cb(Task.fail(e))

    final override protected def terminated(): Unit = cb(Task.fail(actorTerminatedException))
  }.behavior
}
