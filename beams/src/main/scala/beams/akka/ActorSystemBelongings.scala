package beams.akka

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler, SpawnProtocol}

class ActorSystemBelongings(system: ActorSystem[_]) extends HasReceptionist with HasScheduler {
  override def receptionist: ActorRef[Receptionist.Command] = system.receptionist

  override def scheduler: Scheduler = system.scheduler
}

class SpawningSystemBelongings(system: ActorSystem[SpawnProtocol.Command])
  extends ActorSystemBelongings(system)
    with HasSpawnProtocol {
  override def spawnProtocol: ActorRef[SpawnProtocol.Command] = system
}
