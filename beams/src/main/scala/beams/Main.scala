package beams

import _root_.akka.actor.typed._
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.util.Timeout
import beams.akka._
import beams.akka.local._
import scalaz.zio.ZIO._
import scalaz.zio._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object Main {
  def main(args: Array[String]): Unit = {

    def program[N[+ _]]: ZIO[Beam[N, Any], Throwable, Int] = {
      val syntax = new BeamSyntax[N] {}
      import syntax._

      for {
        m1 <- node("test1")
        m2 <- node(10)
        result <- (m1 <*> m2).use { case (n1, n2) =>
          for {
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
      } yield result
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
    system.terminate()
  }
}