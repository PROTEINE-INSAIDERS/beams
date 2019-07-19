package beams

import _root_.akka.actor._
import beams.akka._
import cats._
import cats.arrow.FunctionK
import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import cats.mtl.implicits._

object Main {
  def program[F[_] : Monad, N, R](implicit cluster: Cluster[F, N]) = for {
    nodes <- Cluster[F, N].nodes
  } yield nodes

  def main(args: Array[String]): Unit = {
    implicit val actorSystem = ActorSystem("hujpizda")
    implicit val executionContext = actorSystem.dispatcher
    implicit val contextShift = IO.contextShift(actorSystem.dispatcher)

    val pp = program[AkkaT[IO, ?], ActorRef, NonEmptyList[ActorRef]]

    val io = run[IO, NonEmptyList[ActorRef]](pp, 3, FunctionK.id)
    println(io.unsafeRunSync())

    println("terminating actor system...")
    actorSystem.terminate().onComplete(a => println(a))
    ()
  }
}
