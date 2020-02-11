package beams.backend.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import zio._

import scala.reflect._

private[akka] object ReplyToActor {

  trait Command[+A]

  final case class Done[A](a: A) extends Command[A] with SerializableMessage

  object Terminated extends Command[Nothing] with SerializableMessage

  def apply[A: ClassTag](actor: ActorRef[Nothing], cb: Task[A] => Unit): Behavior[A] = Behaviors.setup[Command[A]] { ctx =>
    guardBehavior(cb) {
      ctx.watchWith(actor, Terminated)
      Behaviors.receiveMessagePartial {
        case Done(a) =>
          cb(Task.succeed(a))
          ctx.unwatch(actor)
          Behaviors.stopped
        case Terminated =>
          cb(Task.fail(new ActorTerminatedException(s"Actor $actor terminated before sending response.", actor)))
          Behaviors.stopped
      }
    }
  }.transformMessages { case a => Done(a) }
}

