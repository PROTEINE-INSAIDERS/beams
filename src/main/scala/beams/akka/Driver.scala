package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._

object Driver {
  type Ref[F[_], A] = ActorRef[Message[F, A]]

  private[akka] sealed trait Message[F[_], +A] extends BeamsMessage

  /**
    * Initialize driver and workers. Should be sent once after driver and workers creation.
    */
  private final case class Initialize[F[_]](workers: NonEmptyList[Worker.Ref[F]]) extends Message[F, Nothing]

  /**
    * This message is sent by each worker to driver in response to Initialize message.
    */
  private[akka] final case class NodeInitialized[F[_]](node: Worker.Ref[F]) extends Message[F, Nothing]

  /**
    * Execute program and send result to specified actor.
    */
  private final case class Run[F[_], A](program: AkkaBeam[F, A], replyTo: ActorRef[A]) extends Message[F, A]

  /**
    * Stop the driver and all it workers.
    */
  private final case class Shutdown[F[_]]() extends Message[F, Nothing]

  private[akka] def apply_old[F[_] : LiftIO, A](nodes: NonEmptyList[Worker.Ref[F]]): Behavior[Message[F, A]] = Behaviors.setup { context =>
    context.log.debug(s"Driver ${context.self} started.")
    Behaviors.receiveMessagePartial[Message[F, A]] {
      case Run(program, replyTo) =>

        def initializing(pending: Set[Worker.Ref[F]]): Behavior[Message[F, A]] = Behaviors.setup { _ =>
          if (pending.isEmpty) {
            val beam = addFinalizer(program, nodes, replyTo)
            nodes.head ! Worker.Run(beam)
            Behaviors.stopped
          } else {
            Behaviors.receiveMessagePartial {
              case NodeInitialized(node) => initializing(pending - node)
            }
          }
        }

        nodes.traverse_ { node => node.tell(Worker.Initialize(nodes, context.self)).pure[Id] }
        initializing(nodes.toList.toSet)
    }.receiveSignal {
      case (context, PostStop) =>
        //TODO: убить всех воркеров
        context.log.debug(s"Driver ${context.self} stopped.")
        Behaviors.same
      case (context, Terminated(value)) =>
        context.log.debug(s"Driver ${context.self} terminated (ref = $value).")
        Behaviors.same
    }
  }

  private def addFinalizer[A, F[_] : LiftIO](
                                              program: AkkaBeam[F, A],
                                              nodes: NonEmptyList[Worker.Ref[F]],
                                              replyTo: ActorRef[A]
                                            ): ReaderT[F, AkkaEnv[F], Unit] = {
    program.run(result => Kleisli(_ => LiftIO[F].liftIO(IO {
      replyTo ! result
      // возможно следует переделать так, чтобы данный актор оставался живым до окончания процесса и убивал
      // всех воркеров после собственного завершения.
      nodes.traverse_ { node => node.tell(Worker.Shutdown()).pure[Id] }
    })))
  }

  private final case class DriverImpl[F[_], A](private val dummy: Boolean = true) extends AnyVal {
    private def guard(f: ActorContext[Message[F, A]] => Behaviors.Receive[Message[F, A]]): Behavior[Message[F, A]] =
      Behaviors.setup { context =>
        f(context).receiveSignal { case (context, PostStop) =>
          context.log.warning(s"Driver ${context.self} forced to stop. Resources mind not be released properly.")
          Behaviors.same
        }
      }

    private def uninitialized: Behavior[Message[F, A]] = guard { context =>
      context.log.debug(s"Driver ${context.self} has started.")
      Behaviors.receiveMessagePartial[Message[F, A]] {
        case Initialize(workers) =>
          workers.traverse_(_.tell(Worker.Initialize(workers, context.self)).pure[Id])
          initializing(workers, workers.toList.toSet)
        case Shutdown() =>
          shuttingDown(List.empty)
      }
    }

    private def initializing(workers: NonEmptyList[Worker.Ref[F]], pending: Set[Worker.Ref[F]]): Behavior[Message[F, A]] = guard(
      Behaviors.setup { context =>
        Behaviors.receiveMessagePartial[Message[F, A]] {
          case _ =>
            Behaviors.same
        }
      })

    private def shuttingDown(workers: List[Worker.Ref[F]]): Behavior[Message[F, A]] = Behaviors.setup { context =>
      context.log.debug(s"Shutting down driver ${context.self}")
      if (workers.isEmpty) {
        ???
      }
    }

    def behaviour: Behavior[Message[F, A]] = uninitialized
  }

  //def submit[F[_], G[_], A](
  //                            driver: Resource[G, Driver.Ref[F, A]],
  //                            program: AkkaBeam[F, A]
  //                          ): Unit = {
  // }
}
