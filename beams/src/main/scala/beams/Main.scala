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
      val syntax = BeamSyntax[N]()
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
              } yield 1
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

    val system = createActorSystem("test")
    val runtime = new DefaultRuntime {}
    val io = beam(program, system, FixedTimeout(20 seconds))
    val r = runtime.unsafeRunSync(io)
    println(r)
    system.terminate()
  }
}