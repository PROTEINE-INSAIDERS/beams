package beams

import _root_.akka.actor.typed._
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.util._
import beams.mtl._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import cats.mtl._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util._

package object akka {
  type AkkaBeam[F[_], A] = ContT[ReaderT[F, AkkaEnv[F], ?], Unit, A]

  implicit def akkaBeam[F[_] : Monad : Defer : LiftIO](
                                                        implicit F: ApplicativeAsk[ReaderT[F, AkkaEnv[F], ?], AkkaEnv[F]]
                                                      ): Beam[AkkaBeam[F, ?]] = new Beam[AkkaBeam[F, ?]] {
    override type Node = Worker.Ref[F]

    @inline
    private def continue[A](a: A)(f: (ReaderT[F, AkkaEnv[F], Unit], AkkaEnv[F]) => F[Unit]): AkkaBeam[F, A] =
      ContT { c =>
        Kleisli { state =>
          f(c(a), state)
        }
      }

    override def beamTo(node: Node): AkkaBeam[F, Unit] = continue(()) { (program, environment) =>
      if (environment.context.self == node) {
        environment.context.log.debug(s"${environment.context.self} beams to itself.")
        program(environment)
      } else {
        environment.context.log.debug(s"${environment.context.self} beaming to $node")
        LiftIO[F].liftIO(IO {
          node ! Worker.Run(program)
        })
      }
    }

    override def nodes: AkkaBeam[F, NonEmptyList[Node]] =
      ApplicativeAsk.readerFE[AkkaBeam[F, ?], AkkaEnv[F]] { state => state.nodes }
  }

  /**
    * Мы тут фактически запускаем код драйвера в монаде программы.
    * Это не нужно, т.к. пользователь не управляет кодом драйвера, с другой стороны, воркеры,
    * на которых исполняется код программы, должны уметь предоставлять дополнительные сервисы,
    * используя монаду программы.
    *
    * Единственный вопрос: следует ли абстрагироваться от контекста исполнения?
    *
    * Абстрагироваться можно через LiftIO, внутри которого будет создано IO-действие.
    */
  def run_driver[F[_] : LiftIO, A](
                                    program: AkkaBeam[F, A],
                                    driver: ActorRef[AkkaDriver.Message[F, A]]
                                  )
                                  (
                                    implicit timeout: Timeout,
                                    scheduler: Scheduler,
                                    contextShift: ContextShift[IO]
                                  ): F[A] = LiftIO[F].liftIO {
    IO.fromFuture(IO {
      driver ? { replyTo =>
        ???
        //Driver.Run[F, A](program, replyTo)
      }
    })
  }

  //TODO: поднимать отдельные действия в базовую монаду, чтобы работал cancel, например.
  //TODO: параметр timeout надо перенести в настройки и обозвать ask timeout.
  // возможно следует создать специальную структуру с таймаутами, потому что таймаут для создания актора и таймаут для
  // выоплнения программы может довольно сильно различаться.
  // TODO: возможно contextShift следует брать из actorSystem (но это не точно).
  @deprecated("deprecated", "1.0")
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

    def createDriver(nodes: NonEmptyList[Worker.Ref[F]]): Future[AkkaDriver.Ref[F, A]] =
      actorSystem ? SpawnProtocol.Spawn(behavior = AkkaDriver.apply_old[F, A](nodes), name = "")

    LiftIO[F].liftIO(for {
      wokers <- IO.fromFuture(IO(createWorkers))
      driver <- IO.fromFuture(IO(createDriver(wokers)))
    } yield driver).flatMap(driver => run_driver(program, driver))
  }
}
