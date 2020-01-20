package beams.akka

import akka.actor.typed.ActorRef

final case class ActorTerminatedException(message: String, actorRef: ActorRef[Nothing]) extends Throwable(message)
