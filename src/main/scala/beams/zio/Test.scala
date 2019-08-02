package beams.zio

import java.io.{ByteArrayOutputStream, ObjectOutputStream}

import scalaz.zio._

object Test {

  def fib(n: Int): UIO[Int] =
    if (n <= 1) {
      IO.succeedLazy(1)
    } else {
      for {
        fiber1 <- fib(n - 2).fork
        fiber2 <- fib(n - 1).fork
        v2 <- fiber2.join
        v1 <- fiber1.join
      } yield v1 + v2
    }

  def main(args: Array[String]): Unit = {
    val runtime = new DefaultRuntime {}
    val a = fib(10)


    val bo = new ByteArrayOutputStream()
    val oo = new ObjectOutputStream(bo)
    oo.writeObject(a)

    val res = runtime.unsafeRun(a)
    println(res )
  }
}
