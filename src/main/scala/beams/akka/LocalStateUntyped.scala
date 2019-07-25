package beams.akka

import akka.{actor => untyped}
import akka.actor.typed._
import cats.data.NonEmptyList

case class LocalStateUntyped(nodes: NonEmptyList[untyped.ActorRef])

case class LocalState[F[_]](nodes: NonEmptyList[ActorRef[Worker.Message[F]]])