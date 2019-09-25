package beams.akka

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import scalaz.zio.{TaskR, ZIO}

trait HasReceptionist {
  def receptionist: ActorRef[Receptionist.Command]
}

trait HasReceptionistSyntax {
  def accessReceptionist: TaskR[HasReceptionist, ActorRef[Receptionist.Command]] = ZIO.access(_.receptionist)
}
