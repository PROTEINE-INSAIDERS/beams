import beams._
import scalaz.zio._

import scala.collection._

object Main {

  trait Source[K, V] {
    def numPartitions: Int

    def partition(n: Int): Iterable[(K, V)]
  }

  private def shrunk(hash: Int, bound: Int): Int = {
    (java.lang.Integer.toUnsignedLong(hash) * bound >>> 32).toInt
  }

  private def addReduced[K, V](data: mutable.Map[K, V], reduce: (V, V) => V, items: Iterable[(K, V)]): mutable.Map[K, V] = {
    for ((key, value) <- items) {
      if (data.contains(key)) {
        data(key) = reduce(data(key), value)
      } else
        data(key) = value
    }
    data
  }

  def mapReduce[N[+ _], K1, V1, K2, V2](
                                         source: Source[K1, V1],
                                         map: (K1, V1) => Iterable[(K2, V2)],
                                         reduce: (V2, V2) => V2
                                       ): ZIO[Beam[N, Any], Throwable, Iterable[(K2, V2)]] = {
    val syntax = BeamSyntax[N]()
    import syntax._
    val capacity = math.min(java.lang.Runtime.getRuntime.availableProcessors(), source.numPartitions)
    for {
      mappers <- ZIO.foreach(0 until capacity)(node)
      reducers <- ZIO.foreach(0 until capacity)(_ => Ref.make(mutable.Map.empty[K2, V2]) >>= node)
      result <- (Managed.collectAll(mappers) <*> Managed.collectAll(reducers)).use {
        case (mappers, reducerList) => for {
          reducers <- ZIO.succeed(reducerList.toVector)
          mapperFibers <- ZIO.foreach(mappers) { mapper =>
            forkTo(mapper) {
              for {
                partitionNo <- env[Int]
                partitionedData <- IO {
                  addReduced(new mutable.HashMap[K2, V2], reduce, source.partition(partitionNo).flatMap { case (k, v) => map(k, v) })
                    .groupBy { case (k, _) => shrunk(k.hashCode(), capacity) }
                }
                reducerFibers <- ZIO.foreachPar(partitionedData) {
                  case (partition, mappedData) => forkTo(reducers(partition)) {
                    for {
                      ref <- env[Ref[mutable.Map[K2, V2]]]
                      _ <- ref.update { reducedData =>
                        addReduced(reducedData, reduce, mappedData)
                      }
                    } yield ()
                  }
                }
                _ <- Fiber.joinAll(reducerFibers)
              } yield ()
            }
          }
          _ <- Fiber.joinAll(mapperFibers)
          collectFibers <- ZIO.foreach(0 until capacity) { partNo =>
            forkTo(reducers(partNo))(env[Ref[mutable.Map[K2, V2]]].flatMap(_.get))
          }
          result <- collectFibers.foldLeft(IO(new mutable.ArrayBuffer[(K2, V2)])) { (io, f) =>
            for {
              collectorData <- io
              reducerData <- f.join
            } yield {
              collectorData.appendAll(reducerData)
              collectorData
            }
          }
        } yield result
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
