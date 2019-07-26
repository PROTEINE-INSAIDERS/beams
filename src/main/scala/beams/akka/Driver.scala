package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import cats._
import cats.data._
import cats.effect._
import cats.implicits._

object Driver {
  type Ref[F[_], A] = ActorRef[Message[F, A]]

  sealed trait Message[F[_], +A] extends BeamsMessage

  final case class Run[F[_], A](program: AkkaBeam[F, A], replyTo: ActorRef[A]) extends Message[F, A]

  final case class NodeInitialized[F[_]](node: Worker.Ref[F]) extends Message[F, Nothing]

  def apply[F[_] : LiftIO, A](nodes: NonEmptyList[Worker.Ref[F]]): Behavior[Message[F, A]] = Behaviors.setup { context =>
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
                                            ): ReaderT[F, Environment[F], Unit] = {
    program.run(result => Kleisli(_ => LiftIO[F].liftIO(IO {
      replyTo ! result
      // возможно следует переделать так, чтобы данный актор оставался живым до окончания процесса и убивал
      // всех воркеров после собственного завершения.
      nodes.traverse_ { node => node.tell(Worker.Shutdown()).pure[Id] }
    })))
  }
}
