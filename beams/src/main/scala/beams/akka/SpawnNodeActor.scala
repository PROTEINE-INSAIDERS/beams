package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

object SpawnNodeActor {
  type Ref = ActorRef[Command]
  type Ctx = ActorContext[Command]

  sealed trait Command extends BeamMessage

  final case class Spawn(replyTo: ActorRef[NodeActor.Ref]) extends Command

  object Stop extends Command

  def apply[Env](env: Env, runtime: DefaultRuntime): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case Spawn(replyTo) =>
        replyTo ! ctx.spawnAnonymous(NodeActor(env, runtime))
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
