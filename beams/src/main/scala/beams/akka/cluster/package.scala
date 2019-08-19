package beams.akka

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.adapter._

package object cluster {
  private val defaultServiceKey: ServiceKey[NodeActor.Command] = ServiceKey[NodeActor.Command]("beams-root-node")

  /**
    * Create beams actor system and join the cluster.
    */
  def createActorSystem(
                         name: String = "beams",
                         setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                         beamsKey: ServiceKey[NodeActor.Command] = defaultServiceKey
                       ): ActorSystem[Nothing] = {
    val untypedSystem = akka.actor.ActorSystem(name, setup)
    val system = untypedSystem.toTyped
    val rootNode = untypedSystem.spawn(NodeActor(()), defaultServiceKey.id)
    system.receptionist ! Receptionist.Register(defaultServiceKey, rootNode)
    system
  }
}
