package beams.akka

import akka.actor.typed.receptionist._

object AkkaBackend extends beams.Backend {
  override type Node[+R] = NodeProtocol.Ref[R]
  override type Key[R] = ServiceKey[NodeProtocol.Command[R]]

  def key[R](id: String): ServiceKey[NodeProtocol.Command[R]] = ServiceKey[NodeProtocol.Command[R]](id)
}
