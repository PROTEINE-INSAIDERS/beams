package beams.akka
/*
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

private[akka] object HomunculusLoxodontus {
  type Ref = ActorRef[Any]

  object Interrupt extends NonSerializableMessage

  def apply[R, A](node: NodeActor.Ref[R], task: TaskR[R, A], cb: Task[A] => Unit): Behavior[Any] = Behaviors.setup { ctx =>
    node ! NodeActor.Exec(task, ctx.self)
    Behaviors.receiveMessagePartial {
      case Interrupt =>
        node ! NodeActor.Interrupt(ctx.self)
        Behaviors.stopped
      case ResultWrapper(r) =>
        cb(ZIO.done(r.asInstanceOf[Exit[Throwable, A]]))
        Behaviors.stopped
    }
  }
}
*/