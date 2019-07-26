package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl.ActorContext
import cats.data.NonEmptyList

case class Environment[F[_]](
                              context: ActorContext[Worker.Message[F]],
                              nodes: NonEmptyList[ActorRef[Worker.Message[F]]]
                            )