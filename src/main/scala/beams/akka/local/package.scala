package beams.akka

import akka.actor.typed._

package object local {
  def createActorSystem(name: String): ActorSystem[SpawnProtocol.Command] = {
    ActorSystem(SpawnProtocol(), name)
  }
}
