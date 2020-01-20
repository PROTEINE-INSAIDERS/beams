package beams.akka

import beams.Engine

final class AkkaEngine extends Engine {
  override type Node[+R] = NodeActor.Ref[R]
  override type NodeKey[T] = akka.actor.typed.receptionist.ServiceKey[NodeActor.Command[T]]
}
