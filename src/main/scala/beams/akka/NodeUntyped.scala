package beams.akka

import akka.actor._
import cats.arrow._
import cats.data._
import cats.effect._

sealed trait NodeMessage

case class BeamMessage[F[_]](beam: ReaderT[F, LocalStateUntyped, Unit])

case class DiscoverMessage(nodes: NonEmptyList[ActorRef])

class NodeActor[F[_]](compiler: FunctionK[F, IO]) extends Actor with ActorLogging {
  private var localState: LocalStateUntyped = _

  override def preStart(): Unit = log.info("NodeActor started")

  override def postStop(): Unit = log.info("NodeActor stopped")

  private def initialized: Receive = {
    case b: BeamMessage[F] =>
      val f = b.beam.run(localState)
      val ioa = compiler(f)
      ioa.unsafeRunSync()
  }


  private def uninitialized: Receive = {
    case DiscoverMessage(nodes) =>
      localState = LocalStateUntyped(nodes)
      context.become(initialized)
  }

  override def receive: Receive = uninitialized
}

object NodeActor {
  def props[F[_]](compiler: FunctionK[F, IO]): Props = Props(new NodeActor[F](compiler))
}