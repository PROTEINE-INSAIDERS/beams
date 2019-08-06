package beams


import beams.akka._
import scalaz.zio.ZIO._
import scalaz.zio._

object Main {
  def main(args: Array[String]): Unit = {

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

    //val rt = new LocalBeam[Any](LocalNode(()))
    //val localProgram = program.provide(rt)
    //val runtime = new DefaultRuntime {}
    //val res = runtime.unsafeRun(localProgram)
    //println(res)
  }
}