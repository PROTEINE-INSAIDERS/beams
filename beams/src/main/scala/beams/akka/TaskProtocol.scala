package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._

object TaskProtocol {
  sealed trait Command

  def apply(): Behavior[Command] = Behaviors.stopped
}
