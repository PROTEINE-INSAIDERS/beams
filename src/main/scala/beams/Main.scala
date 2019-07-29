package beams

import _root_.akka.actor.typed._
import _root_.akka.util._
import beams.akka._
import beams.mtl._
import cats._
import cats.data._
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import cats.mtl._
import cats.mtl.implicits._
import monix.eval._
import monix.eval.instances._
import monix.execution.Scheduler.Implicits.global

import scala.concurrent._
import scala.concurrent.duration._

final case class UserEnv()

object Main {
  type UserProgram[A] = AkkaBeam[ReaderT[Task, UserEnv, ?], A]

  def program[F[_] : Monad, A](implicit Beam: Beam[F], Env: ApplicativeAsk[F, UserEnv]): F[Int] = for {
    nodes <- Beam.nodes
    _ <- Beam.beamTo(nodes.head)
  } yield nodes.size

 // def programTF[F[_] : Monad, A](implicit F: BeamTF[F]): F[Int] = 0.pure[F]

  def main(args: Array[String]): Unit = {
    implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

    /*
    implicit val system = ActorSystem(SpawnProtocol.behavior, "hujpizda")
    implicit val timeout = Timeout(3.seconds)

    val pp = program[UserProgram, Int](
      Monad[UserProgram] ,
      Beam[UserProgram],
      implicitly
    )

    val ggg : Task[Int] = ???
    val liftIO = LiftIO[Task]

*/
    // ggg.liftIO{???}

    // λ[Task ~> IO](_.toIO)

   //  val p = run_spawn(pp, ???, 3)

    //val res = p.runSyncUnsafe()
    //println(res)
    //system.terminate()

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