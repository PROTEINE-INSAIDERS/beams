package beams

import java.io.{ByteArrayOutputStream, ObjectOutputStream}

import _root_.akka.{pattern => untypedPattern}
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.util._
import _root_.akka.{actor => untyped}
import beams.mtl._
import cats._
import cats.arrow.FunctionK
import cats.data._
import cats.effect.{IO, LiftIO, _}
import cats.implicits._
import cats.mtl._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util._

package object akka {
  @deprecated("use AkkaBeam", "0.1")
  type AkkaT[F[_], A] = ContT[ReaderT[F, LocalStateUntyped, ?], Unit, A]

  type AkkaBeam[F[_], A] = ContT[ReaderT[F, LocalState[F], ?], Unit, A]

  def checkSerializable(a: Any): Unit = {
    val bs = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(bs)
    println(s"==== checking $a is serializable")
    oos.writeObject(a)
    println(s"==== $a serializad size: ${bs.size()}")
  }

  @deprecated("use beamTFForAkkaLocal", "0.1")
  implicit def beamForAkkaLocal[F[_] : Monad : Defer](
                                                       implicit F: ApplicativeAsk[ReaderT[F, LocalStateUntyped, ?], LocalStateUntyped]
                                                     ): Beam[AkkaT[F, ?], untyped.ActorRef] = new Beam[AkkaT[F, ?], untyped.ActorRef] {
    override def beamTo(node: untyped.ActorRef): AkkaT[F, Unit] = ContT { cont =>

      val payload = cont(())
      checkSerializable(payload)

      ().pure[ReaderT[F, LocalStateUntyped, ?]]
    }

    override def newIVar[A]: AkkaT[F, (AkkaT[F, A], A => AkkaT[F, Unit])] = ContT { cont =>
      //TODO: создать новый актор.
      ???
    }

    override def nodes: AkkaT[F, NonEmptyList[untyped.ActorRef]] = ApplicativeAsk.readerFE[AkkaT[F, ?], LocalStateUntyped] { n =>
      println(s"==== Nodes ${n.nodes}")
      n.nodes
    }
  }

  implicit def beamTFForAkkaLocal[F[_] : Monad : Defer](
                                                         implicit F: ApplicativeAsk[ReaderT[F, LocalState[F], ?], LocalState[F]]
                                                       ): BeamTF[AkkaBeam[F, ?]] = new BeamTF[AkkaBeam[F, ?]] {
    override type Node = Worker.Ref[F]

    override def beamTo(node: Node): AkkaBeam[F, Unit] = ???

    override def nodes: AkkaBeam[F, NonEmptyList[Node]] = ApplicativeAsk.readerFE[AkkaBeam[F, ?], LocalState[F]] { state => state.nodes }
  }

  def run_driver[F[_] : LiftIO, A](
                                    program: AkkaBeam[F, A],
                                    driver: ActorRef[Driver.Message[F, A]]
                                  )
                                  (
                                    implicit timeout: Timeout,
                                    scheduler: Scheduler,
                                    contextShift: ContextShift[IO]
                                  ): F[A] = LiftIO[F].liftIO {
    checkSerializable(program)

    IO.fromFuture(IO {
      driver ? { replyTo =>
        println("==== is replyTo serializable?")
        checkSerializable(replyTo )
        Driver.Run[F, A](program, replyTo)
      }
    })
  }

  //TODO: поднимать отдельные действия в базовую монаду, чтобы работал cancel, например.
  //TODO: параметр timeout надо перенести в настройки и обозвать ask timeout.
  // возможно следует создать специальную структуру с таймаутами, потому что таймаут для создания актора и таймаут для
  // выоплнения программы может довольно сильно различаться.
  // TODO: возможно contextShift следует брать из actorSystem (но это не точно).
  // TODO: actorSystem не сериализуема, нужно чтобы она не попадала в лямбды.
  def run_spawn[F[_] : Monad : LiftIO, A](
                                           program: AkkaBeam[F, A],
                                           compiler: F ~> IO,
                                           workerCount: Int
                                         )
                                         (
                                           implicit timeout: Timeout,
                                           actorSystem: ActorSystem[SpawnProtocol],
                                           contextShift: ContextShift[IO]
                                         ): F[A] = {
    if (workerCount == 0) throw new IllegalArgumentException("Worker count should be greater than zero")

    implicit val scheduler: Scheduler = actorSystem.scheduler
    implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

    def createWorker(): Future[Worker.Ref[F]] = actorSystem ? SpawnProtocol.Spawn(behavior = Worker(compiler), name = "")

    def addWorker(workers: NonEmptyList[Worker.Ref[F]]): Future[NonEmptyList[Worker.Ref[F]]] =
      createWorker().transform {
        case Success(value) => Success(value :: workers)
        case Failure(exception) =>
          workers.traverse_[Id, Unit](_ ! Worker.Shutdown[F]())
          Failure(exception)
      }

    def createWorkers: Future[NonEmptyList[Worker.Ref[F]]] = for {
      head <- createWorker()
      workers <- (workerCount - 1, NonEmptyList.one(head)).tailRecM { case (c, wx) =>
        if (c == 0) {
          Future.successful(Either.right[(Int, NonEmptyList[Worker.Ref[F]]), NonEmptyList[Worker.Ref[F]]](wx))
        } else {
          addWorker(wx).map(wxx => Either.left[(Int, NonEmptyList[Worker.Ref[F]]), NonEmptyList[Worker.Ref[F]]]((c - 1, wxx)))
        }
      }
    } yield workers

    def createDriver(nodes: NonEmptyList[Worker.Ref[F]]): Future[Driver.Ref[F, A]] =
      actorSystem ? SpawnProtocol.Spawn(behavior = Driver[F, A](nodes), name = "")

    LiftIO[F].liftIO(for {
      wokers <- IO.fromFuture(IO(createWorkers))
      driver <- IO.fromFuture(IO(createDriver(wokers)))
    } yield driver).flatMap(driver => run_driver(program, driver))
  }


  @deprecated("use run_spawn", "1.0")
  def run[F[_] : LiftIO : Monad, A](
                                     process: AkkaT[F, A],
                                     nodesCount: Int,
                                     compiler: FunctionK[F, IO]
                                   )
                                   (
                                     implicit actorSystem: untyped.ActorSystem,
                                     // contextShift: ContextShift[IO]
                                   ): F[A] = LiftIO[F].liftIO(for {
    // dresult <- Deferred[IO, A]
    actors <- IO {
      NonEmptyList.fromListUnsafe(List.fill(nodesCount)(actorSystem.actorOf(NodeActor.props(compiler))))
    }
    _ <- actors.traverse_ { actor =>
      IO {
        actor ! DiscoverMessage(actors)
      }
    }
    _ <- IO {
      actors.head ! BeamMessage[F](process.run { aaa =>
        ReaderT { env =>
          LiftIO[F].liftIO(IO {
            println("=== в рот")
            ()
          } /* *> dresult.complete(aaa) */)
        }
      })
    }
    // result <- dresult.get
  } yield ???)
}
