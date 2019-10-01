package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

private[akka] object HomunculusLoxodontus {
  type Ref[-A] = ActorRef[Command[A]]

  sealed trait Command[+A]

  final case class Wait[A](cb: Task[A] => Unit) extends Command[A]

  /**
    * Interrupt remote task.
    *
    * Please note that this message will not shut down the Homunculus Loxodontus itself and will not invoke
    * callback with ZIO.interrupt argument. To shut down Homunculus Loxodontus send [[Shutdown]] message.
    */
  object Interrupt extends Command[Nothing] with NonSerializableMessage

  /**
    * Shut down Homunculus Loxodontus. Will also send interrupt message to remote task if it not now to be completed.
    */
  object Shutdown extends Command[Nothing] with NonSerializableMessage

  def apply[R, A](node: NodeProtocol.Ref[R], task: TaskR[R, A]): Behavior[Any] = Behaviors.setup { ctx =>
    // TODO: возможно нам следует следить за задачей с помощью ctx.watch()

    //node ! NodeActor.Exec(task, ctx.self)
    Behaviors.receiveMessagePartial {
      case Interrupt =>
        // node ! NodeActor.Interrupt(ctx.self)
        Behaviors.same
      case ResultWrapper(r) =>
        //   cb(ZIO.done(r.asInstanceOf[Exit[Throwable, A]]))
        Behaviors.stopped
    }
  }
}
