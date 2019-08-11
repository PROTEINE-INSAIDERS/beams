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

    def program[N[+_]]: ZIO[Beam[N, Any], Throwable, Int] = {
      val syntax = new BeamSyntax[N] {}
      import syntax._

      for {
        n1 <- createNode("str1")
        n2 <- createNode(10)
        f1 <- forkTo(n1) {
          for {
            s <- self[String]
            n3 <- createNode(true)
          } yield ()
          effectTotal(println("")).andThen(succeed(1))
        }
        f2 <- forkTo(n2) {
          for {
            r <- env[Int]
          } yield r
        }
        r1 <- f1.join
        r2 <- f2.join
      } yield r1 + r2
    }

    val system: ActorSystem[SpawnProtocol.Command] = createActorSystem("test")
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    val rootFuture = system.ask[NodeActor.Ref](SpawnProtocol.Spawn(NodeActor(()), "", Props.empty, _))

    implicit val ec = ExecutionContext.global

    val a = for {
      root <- rootFuture
      result <- root.ask[Exit[Throwable, Any]](replyTo => NodeActor.RunTask(program, replyTo, FixedTimeout(20 seconds)))
    } yield result

    val r = Await.result(a, 10 seconds)
    println(r)

    // io.StdIn.readLine()
    system.terminate()
  }
}