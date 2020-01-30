package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import zio._
import zio.console._

private[akka] object DeathWatch {

  trait Command

  object Stop extends Command with NonSerializableMessage

  object Stopped extends Command with SerializableMessage

  def apply(actor: ActorRef[Nothing], cb: Task[Unit] => Unit): Behavior[Command] = Behaviors.setup { ctx =>
      guard(cb) {
        ctx.watchWith(actor, Stopped)

        Behaviors.receiveMessagePartial {
          case
        }
      }
    }
  }


object Main {
  def main(args: Array[String]): Unit = {
    val a: ZIO[Console, Throwable, Unit] = for {
      i <- Task.effectAsync { (cb: Task[Int] => Unit) =>
        cb(ZIO.succeed(19))
        cb(ZIO.succeed(20))
      }
      _ <- putStrLn(i.toString)
    } yield ()

    val runtime = new DefaultRuntime {}
    runtime.unsafeRun(a)
  }

}