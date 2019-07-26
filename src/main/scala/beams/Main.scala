package beams

import _root_.akka.actor.typed._
import _root_.akka.util._
import beams.akka._
import cats._
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import cats.mtl.implicits._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent._
import scala.concurrent.duration._

object Main {

  def program[F[_] : Monad, A](implicit F: Beam[F]): F[Int] = for {
    nodes <- F.nodes
    _ <- F.beamTo(nodes.head)
  } yield nodes.size

 // def programTF[F[_] : Monad, A](implicit F: BeamTF[F]): F[Int] = 0.pure[F]

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(SpawnProtocol.behavior, "hujpizda")
    implicit val timeout = Timeout(3.seconds)
    implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

    val pp = program[AkkaBeam[Task, ?], Int]

    val p = run_spawn(pp, λ[Task ~> IO](_.toIO), 3)
    val res = p.runSyncUnsafe()
    println(res)
    system.terminate()
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