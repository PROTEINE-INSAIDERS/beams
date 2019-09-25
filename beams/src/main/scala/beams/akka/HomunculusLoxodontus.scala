package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

private[akka] object HomunculusLoxodontus {
  type Ref = ActorRef[Any]

  object Cancel extends NonSerializableMessage

  def apply[R, A](node: Node.Ref[R], task: TaskR[R, A], cb: Task[A] => Unit): Behavior[Any] = Behaviors.setup { ctx =>
    node ! Node.Exec(task, ctx.self)
    Behaviors.receiveMessagePartial {
      case Cancel =>
        node ! Node.Cancel(ctx.self)
        Behaviors.stopped
      case r: Exit[_, _] =>
        cb(ZIO.done(r.asInstanceOf[Exit[Throwable, A]]))
        Behaviors.stopped
    }
  }
}
