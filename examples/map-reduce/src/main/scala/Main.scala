import beams._
import scalaz.zio._

import scala.collection._
import scala.reflect.ClassTag

object Main {

  trait Source[K, V] {
    def keys: Iterable[K]

    def values(k: K): Iterable[V]
  }

  private def shrunk(hash: Int, bound: Int): Int = {
    (java.lang.Integer.toUnsignedLong(hash) * bound >>> 32).toInt
  }

  def mapReduce[N[+ _], K1, V1, K2, V2](
                                         source: Source[K1, V1],
                                         map: (K1, V1) => Iterable[(K2, V2)],
                                         reduce: (V2, V2) => V2
                                       )
                                       (implicit e1: ClassTag[N[Int]]
                                        // ,e2: ClassTag[N[mutable.Map[K, V]]]
                                       ): ZIO[Beam[N, Any], Throwable, Unit] = {
    val syntax = BeamSyntax[N]()
    import syntax._
    val capacity = java.lang.Runtime.getRuntime.availableProcessors()
    for {
      mappers <- ZIO.foreach(source.keys)(node)
      reducers <- ZIO.foreach(0 until capacity)(_ => Ref.make(mutable.Map.empty[K2, V2]) >>= node)
      result <- (Managed.collectAll(mappers) <*> Managed.collectAll(reducers)).use { case (mappers, reducerList) => for {
        reducers <- ZIO(reducerList.toArray)
        aaaa <- ZIO.foreachPar(mappers) { mapper => ??? }
      }  yield ???
      /*
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
       */
    } yield ???
  }

  def main(args: Array[String]): Unit = {
    val r = scala.util.Random
    for (i <- 0 to 100) {
      println(shrunk(r.nextInt(), 8))
    }
  }
}
