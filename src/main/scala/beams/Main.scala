package beams

import _root_.akka.actor._
import beams.akka._
import cats._
import cats.arrow.FunctionK
import cats.data.{EitherT, NonEmptyList}
import cats.effect._
import cats.implicits._
import cats.mtl.implicits._
import javafx.scene.shape.MoveTo
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent._

object Main {
  def program[F[_] : Monad, N, R](implicit F: Beam[F, N]): F[NonEmptyList[N]] = for {
    nodes <- F.nodes
    _ <- F.beamTo(nodes.head)
  } yield nodes

  def programTF[F[_] : Monad, A](implicit F: BeamTF[F]): F[NonEmptyList[F.Node]] = for {
    nodes <- F.nodes
    _ <- F.beamTo(nodes.head)
  } yield nodes


  def main(args: Array[String]): Unit = {

    // 1. должна быть монадка по ether, внутри которой лежит future (т.е. в определенный момент мы должны прекратить
    // биндить future и вернуть left)

    //val aaa: EitherT[Future, String, Int] = for {
    //  aaaa <- Future(10).transform(???).traverse(???)
    //} yield ???

    //def test(l: List[Int], i : Int): Future[Either[List[Int], List[Int]]] = {
    //  Future
    //}


    implicit val actorSystem: ActorSystem = ActorSystem("hujpizda")
    // implicit val executionContext = actorSystem.dispatcher
    implicit val contextShift: ContextShift[IO] = IO.contextShift(actorSystem.dispatcher)

    val pp = program[AkkaT[Task, ?], ActorRef, NonEmptyList[ActorRef]]

    val f = λ[Vector ~> List](_.toList)

    val io = run[Task, NonEmptyList[ActorRef]](pp, 3, λ[Task ~> IO](_.to[IO])

      //new (Task ~> IO){
      //override def apply[A](fa: Task[A]): IO[A] = fa.to[IO]
    //}

    )
    println(io.runSyncUnsafe())

    println("terminating actor system...")
    actorSystem.terminate().onComplete(a => println(a))
    ()
  }
}
