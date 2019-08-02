package beams

import cats.data.NonEmptyList
import scalaz.zio._

package object local {
  type MyError = Throwable

  case class MyIO[A](run: IO[MyError, A]) extends AnyVal

  def createRemote[E](): IO[E, Remote.Service[MyIO]] = for {
    ref <- Ref.make(10)
  } yield new Remote.Service[MyIO] {
    override type Node = Unit

    override def nodes: MyIO[NonEmptyList[Unit]] = MyIO(ZIO(NonEmptyList.one(())))

    override def forkTo[A](node: Unit)(f: MyIO[A]): MyIO[Unit] = MyIO(f.run.fork.unit)
  }
}
