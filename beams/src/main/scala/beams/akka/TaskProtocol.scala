package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

object TaskProtocol {

  sealed trait Command

  /**
    * Interrupt current task and shut down the actor.
    */
  object Interrupt extends Command

  /**
    * Shut down current actor. This message will not interrupt current task.
    */
  private object Shutdown extends Command

  def apply[R, A](task: TaskR[R, A], runtime: Runtime[R], cb: Task[A] => Unit): Behavior[Command] = Behaviors.setup { ctx =>
    val promise = runtime.unsafeRun(Promise.make[Throwable, Fiber[Throwable, A]])
    val interruptibleTask = for {
      fiber <- task.fork
      _ <- promise.succeed(fiber)
      result <- fiber.join
    } yield result
    runtime.unsafeRunAsync(interruptibleTask.ensuring(IO.effectTotal(ctx.self ! Shutdown))) { exit =>
      cb(Task.done(exit))
    }
    Behaviors.receiveMessagePartial {
      case Interrupt =>
        runtime.unsafeRunSync(promise.await.flatMap(_.interrupt))
        Behaviors.same
      case Shutdown =>
        Behaviors.stopped
    }
  }
}
