package beams.akka

import beams.Backend

final class AkkaBackend extends Backend {
  override type Node[+R] = NodeActor.Ref[R]
  override type NodeKey[T] = akka.actor.typed.receptionist.ServiceKey[NodeActor.Command[T]]
}
