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

  sealed trait Message[F[_]] {
    checkSerializable(this)
  }

  final case class Initialize[F[_]](
                                     nodes: NonEmptyList[ActorRef[Worker.Message[F]]],
                                     driver: ActorRef[Driver.Message[F, Nothing]]
                                   ) extends Message[F]

  final case class Run[F[_], A](program: ReaderT[F, LocalState[F], Unit]) extends Message[F]

  final case class Shutdown[F[_]]() extends Message[F]

  def apply[F[_], A](compile: F ~> IO): Behavior[Message[F]] = Behaviors.setup { context =>
    Behaviors.receiveMessagePartial {
      case Initialize(nodes, driver) =>
        val state = LocalState(nodes)
        driver ! Driver.NodeInitialized(context.self)
        Behaviors.receiveMessagePartial {
          case Run(program) =>
            compile(program(state)).unsafeRunSync()
            Behaviors.same
          case Shutdown() => Behaviors.stopped
        }
    }
  }
}