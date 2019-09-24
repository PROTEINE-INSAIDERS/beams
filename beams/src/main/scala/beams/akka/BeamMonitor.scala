package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._

@deprecated("Dynamic node creation deprecated", "0.1")
object BeamMonitor {
  type Ref = ActorRef[Command]

  sealed trait Command extends SerializableMessage

  final case class NodeCreated(node: NodeActor.Ref[_]) extends Command

  final case class NodeReleased(node: NodeActor.Ref[_]) extends Command

  final object Shutdown extends Command

  def apply[Env](): Behavior[Command] = BeamMonitor(Set.empty)

  private def apply(nodes: Set[NodeActor.Ref[_]]): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case NodeCreated(node) => BeamMonitor(nodes + node)
      case NodeReleased(node) => BeamMonitor(nodes - node)
      case Shutdown =>
        ctx.log.warn("Some nodes has not been released before process completion. They will be stopped by Monitor.")
        nodes.foreach(_ ! NodeActor.Stop)
        Behaviors.stopped
    }
  }
}
