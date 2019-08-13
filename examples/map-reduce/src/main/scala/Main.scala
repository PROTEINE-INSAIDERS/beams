import beams._
import scalaz.zio._

import scala.collection._
import scala.reflect.ClassTag

object Main {

  trait Source[A] {
    def numPartitons: Int

    def partition(n: Int): Iterable[A]
  }

  private def shrunk(x: Int, bound: Int): Int = {
    (java.lang.Integer.toUnsignedLong(x) * bound >>> 32).toInt
  }

  def mapReduce[N[+ _], A, K, V](source: Source[A], map: Iterable[A] => Iterable[(K, V)], reduce: (V, V) => V)
                                (implicit e1: ClassTag[N[Int]],
                                 e2: ClassTag[N[mutable.Map[K, V]]]): ZIO[Beam[N, Any], Throwable, Unit] = {
    val syntax = new BeamSyntax[N] {}
    import syntax._
    val capacity = scala.math.min(java.lang.Runtime.getRuntime.availableProcessors(), source.numPartitons)
    for {
      mappers <- ZIO.foreach(0 until capacity)(node)
      reducers <- ZIO.foreach(0 until capacity)(_ => node(mutable.Map.empty[K, V]))
      result <- (Managed.collectAll(mappers) <*> Managed.collectAll(reducers)).use { case (mapperList, reducerList) => for {
        mappers <- ZIO(mapperList.toArray)
        reducers <- ZIO(reducerList.toArray)
        aaaa <- ZIO.foreach(mappers) { mapper =>
          forkTo(mapper) {
            for {
              partition <- env[Int]
              data <- ZIO(map(source.partition(partition)))
              _ <- ZIO.foreach(data) { case (key, value) => for {
                _ <- forkTo(???) {
                  ???
                }
              } yield ()
              }
            } yield ???
          }
        }
      } yield ()
      }
    } yield result
  }

  def main(args: Array[String]): Unit = {
    val r = scala.util.Random
    for (i <- 0 to 100) {
      println(shrunk(r.nextInt(), 8))
    }
  }
}
