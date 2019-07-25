package beams

import _root_.akka.actor.typed._
import _root_.akka.util._
import beams.akka._
import cats._
import cats.implicits._
import cats.arrow.FunctionK
import cats.data.{EitherT, NonEmptyList}
import cats.effect._
import cats.implicits._
import cats.mtl.implicits._
import javafx.scene.shape.MoveTo
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import cats.effect.implicits._
import scala.concurrent._
import scala.util._
import scala.util.control.NonFatal
import scala.concurrent.duration._

object Main {

  def programTF[F[_] : Monad, A](implicit F: BeamTF[F]): F[Int] = for {
    nodes <- F.nodes
    _ <- F.beamTo(nodes.head)
  } yield nodes.size


  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(SpawnProtocol.behavior, "hujpizda")
    implicit val timeout = Timeout(3.seconds)
    implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

    val pp = programTF[AkkaTF[IO, ?], Int](Monad[AkkaTF[IO, ?]], beamTFForAkkaLocal[IO]())

    run_spawn(pp, FunctionK.id[IO], 3)

    /*
    implicit val actorSystem: ActorSystem = ActorSystem("hujpizda")
    // implicit val executionContext = actorSystem.dispatcher
    implicit val contextShift: ContextShift[IO] = IO.contextShift(actorSystem.dispatcher)

   // val io = run[IO, NonEmptyList[ActorRef]](pp, 3, FunctionK.id[IO])

  //  println(io.unsafeRunSync())

   // println("terminating actor system...")
  //  actorSystem.terminate().onComplete(a => println(a))
     */
    ()
  }
}