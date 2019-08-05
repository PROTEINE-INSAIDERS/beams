package beams


import beams.local.{LocalBeam, LocalNode}
import scalaz.zio.ZIO._
import scalaz.zio._

class MyClass[T] {
  def aaa(t: T) = ()
}

object Main {

  trait MyNode[A] {

  }

  def main(args: Array[String]): Unit = {

    val cc = new MyClass[Int]

    import cc._

    aaa(29)

    def program[Node[_]]: TaskR[Beam[Node, Any], Unit] = for {
      n1 <- spawn[Node, Any, String]("str1")
      n2 <- spawn[Node, Any, Int](10)
      f1 <- forkTo[Node, Any, String, Unit](n1) {
        /*
        for {
          _ <- accessM[String] { env => effectTotal(println(env)) }
          // n3 <- spawn[Node, Boolean](true)
        } yield ()
*/
        effectTotal(println(""))
      }
      f2 <- forkTo[Node, Any, Int, Unit](n2) {
        accessM { env => effectTotal(println(env)) }
      }
      r1 <- f1.join
      r2 <- f2.join
    } yield ()

    val rt = new LocalBeam[Any](LocalNode(()))

    val localProgram = program.provide(rt)

    val runtime = new DefaultRuntime {}

    val res = runtime.unsafeRun(localProgram)

    println(res)
  }
}