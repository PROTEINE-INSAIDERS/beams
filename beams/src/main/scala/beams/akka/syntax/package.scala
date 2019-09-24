package beams.akka

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.{ActorRef, ActorSystem}
import beams.BeamSyntax

package object syntax extends BeamSyntax[Node] {
  implicit def actorSystemBelongings(actorSystem: ActorSystem[_]): HasReceptionist = new HasReceptionist {
    override def receptionist: ActorRef[Receptionist.Command] = actorSystem.receptionist
  }
}
