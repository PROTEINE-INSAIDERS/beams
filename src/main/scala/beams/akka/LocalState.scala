package beams.akka

import akka.actor.ActorRef
import cats.data.NonEmptyList

case class LocalState(nodes: NonEmptyList[ActorRef])