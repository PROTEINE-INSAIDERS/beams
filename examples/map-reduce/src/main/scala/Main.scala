import beams._
import beams.akka.local.{beam, createActorSystem}
import beams.akka.FixedTimeout
import scalaz.zio._

import scala.collection._
import scala.concurrent.duration._

/**
  * Please note that the sole purpose of this example is to demonstrate how distributed programming patterns
  * could be implemented using the Beams library. This example should not be considered as a "best practice"
  * for a real map-reduce implementations and does not meant to be used in any real applications.
  */
object Main {

  trait Source[V] {
    def numPartitions: Int

    def partition(n: Int): Iterable[V]
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

  def mapReduce[N[+ _], V1, K2, V2](
                                     source: Source[V1],
                                     map: V1 => Iterable[(K2, V2)],
                                     reduce: (V2, V2) => V2
                                   ): ZIO[Beam[N, Any], Throwable, Iterable[(K2, V2)]] = {
    val syntax = BeamSyntax[N]()
    import syntax._

    val reducersNum = math.min(java.lang.Runtime.getRuntime.availableProcessors(), source.numPartitions)
    for {
      mappers <- ZIO.foreach(0 until source.numPartitions)(node)
      reducers <- ZIO.foreach(0 until reducersNum)(_ => Ref.make[mutable.Map[K2, V2]](mutable.Map.empty[K2, V2]) >>= node)
      result <- (Managed.collectAll(mappers) <*> Managed.collectAll(reducers)).use {
        case (mappers, reducerList) => for {
          reducers <- ZIO.succeed(reducerList.toVector)
          mapperFibers <- ZIO.foreach(mappers) { mapper =>
            forkTo(mapper) {
              for {
                partitionNo <- env[Int]
                partitionedData <- IO {
                  addReduced(new mutable.HashMap[K2, V2], reduce, source.partition(partitionNo).flatMap(map))
                    .groupBy { case (k, _) => shrunk(k.hashCode(), reducersNum) }
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
          collectFibers <- ZIO.foreach(0 until reducersNum) { partNo =>
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
    val system = createActorSystem("test")
    val runtime = new DefaultRuntime {}

    def program[N[+ _]] = mapReduce[N, String, String, Int](
      source = new Source[String] {
        private val data: String =
          """Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut
            |labore et dolore magna aliqua. Dolor sed viverra ipsum nunc aliquet bibendum enim. In massa
            |tempor nec feugiat. Nunc aliquet bibendum enim facilisis gravida. Nisl nunc mi ipsum faucibus
            |vitae aliquet nec ullamcorper. Amet luctus venenatis lectus magna fringilla. Volutpat maecenas
            |volutpat blandit aliquam etiam erat velit scelerisque in. Egestas egestas fringilla phasellus
            |faucibus scelerisque eleifend. Sagittis orci a scelerisque purus semper eget duis. Nulla
            |pharetra diam sit amet nisl suscipit. Sed adipiscing diam donec adipiscing tristique
            |risus nec feugiat in. Fusce ut placerat orci nulla. Pharetra vel turpis nunc eget lorem dolor.
            |Tristique senectus et netus et malesuada.
            |
            |Etiam tempor orci eu lobortis elementum nibh tellus molestie. Neque egestas congue quisque egestas.
            |Egestas integer eget aliquet nibh praesent tristique. Vulputate mi sit amet mauris. Sodales neque
            |sodales ut etiam sit. Dignissim suspendisse in est ante in. Volutpat commodo sed egestas egestas.
            |Felis donec et odio pellentesque diam. Pharetra vel turpis nunc eget lorem dolor sed viverra.
            |Porta nibh venenatis cras sed felis eget. Aliquam ultrices sagittis orci a. Dignissim diam quis
            |enim lobortis. Aliquet porttitor lacus luctus accumsan. Dignissim convallis aenean et tortor
            |at risus viverra adipiscing at.
            |"""

        private val partitions: Vector[String] = data.stripMargin.replaceAll(raw"[\p{Punct}]", "").toLowerCase.lines.toVector

        override def numPartitions: Int = partitions.length

        override def partition(n: Int): Iterable[String] = List(partitions(n))
      },
      map = _.split("\\s+").map((_, 1)),
      reduce = _ + _
    )

    val io = beam(program, system, FixedTimeout(20 seconds))
    val Exit.Success(r) = runtime.unsafeRunSync(io)

    println("======== res")
    r.toList.sortBy(_._2).foreach(println)

    system.terminate()
  }
}
