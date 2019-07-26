package beams.akka

import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import cats.data.{NonEmptyList, ReaderT}

object Worker {
  type Ref[F[_]] = ActorRef[Message[F]]

  sealed trait Message[F[_]] extends BeamsMessage

  final case class Initialize[F[_]](
                                     nodes: NonEmptyList[ActorRef[Worker.Message[F]]],
                                     driver: ActorRef[Driver.Message[F, Nothing]]
                                   ) extends Message[F]

  final case class Run[F[_], A](program: ReaderT[F, Environment[F], Unit]) extends Message[F]

  final case class Shutdown[F[_]]() extends Message[F]

  def apply[F[_], A](compile: F ~> IO): Behavior[Message[F]] = Behaviors.setup { context =>
    context.log.debug(s"Worker ${context.self} started.")
    Behaviors.receiveMessagePartial {
      case Initialize(nodes, driver) =>
        context.log.debug(s"Worker ${context.self} initialized (with ${nodes.size} peers).")
        val state = Environment(context, nodes)
        driver ! Driver.NodeInitialized(context.self)
        Behaviors.receiveMessagePartial {
          case Run(program) =>
            context.log.debug(s"Worker ${context.self} received run program message: $program.")
            compile(program(state)).unsafeRunSync()
            Behaviors.same
          case Shutdown() =>
            context.log.debug(s"Worker ${context.self} received shutdown message.")
            Behaviors.stopped
        }
    }
  }
}