package beams.backend.akka

import beams.Backend

final class AkkaBackend extends Backend {
  override type Node[+R] = NodeActor.Ref[R]
}
