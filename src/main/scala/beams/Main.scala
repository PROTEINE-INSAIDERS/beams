package beams

import _root_.akka.actor.typed._
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.util.Timeout
import beams.akka._
import beams.akka.local._
import scalaz.zio.ZIO._
import scalaz.zio._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {

    //TODO: глобальный таймаут
    // он должен задаваться при запуске программы (возможно, параметр startTime и параметр endTime, все остальные
    // тамйауты должны вычисляться исходя из этих двух параметров)
    def program: ZIO[Beam[AkkaNode, Any], Throwable, Unit] = for {
      n1 <- createNode("str1")
      n2 <- createNode(10)
      f1 <- forkTo(n1) {
        for {
          s <- self[String]
          n3 <- createNode(true)
        } yield ()
        effectTotal(println("")).andThen(succeed(10))
      }
      f2 <- forkTo[Int, Unit](n2) {
        accessM { env => effectTotal(println(env)) }
      }
      r1 <- f1.join
      r2 <- f2.join
    } yield ()

    println(Map(1 -> "крокодил", 2 -> "сыр"))

    val system: ActorSystem[SpawnProtocol.Command] = createActorSystem("test")
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    val rootFuture = system.ask[NodeActor.Ref](SpawnProtocol.Spawn(NodeActor(()), "", Props.empty, _))

    implicit val ec = ExecutionContext.global

    val a = for {
      root <- rootFuture
      result <- root.ask[NodeActor.TaskFinished](replyTo => NodeActor.RunTask(program, replyTo))
    } yield result


    val r = Await.result(a, 10 seconds)
    println(r)

    /*

    a.onSuccess { case ref: ActorRef[NodeActor.Ref] =>
      ref.ask[TaskFinished](replyTo => NodeActor.RunTask(program, replyTo) )
    }
     */
    // io.StdIn.readLine()
    system.terminate()
  }
}