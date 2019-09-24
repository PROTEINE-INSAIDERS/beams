package beams.akka

import akka.actor.typed.{ActorRef, SpawnProtocol}
import scalaz.zio.{TaskR, ZIO}

//TODO: не используется, удалить.
trait HasSpawnProtocol {
  def spawnProtocol: ActorRef[SpawnProtocol.Command]
}

trait HasSpawnProtocolSyntax {
  def accessSpawnProtocol: TaskR[HasSpawnProtocol, ActorRef[SpawnProtocol.Command]] = ZIO.access(_.spawnProtocol)
}