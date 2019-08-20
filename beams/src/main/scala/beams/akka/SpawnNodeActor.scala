package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

object SpawnNodeActor {
  type Ref[+Env] = ActorRef[Command[Env]]

  sealed trait Command[-Env] extends BeamMessage

  final case class Spawn[Env](replyTo: ActorRef[NodeActor.Ref[Env]]) extends Command[Env]

  object Stop extends Command[Nothing]

  def apply[Env](env: Env, runtime: DefaultRuntime): Behavior[Command[Env]] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case Spawn(replyTo) =>
        replyTo ! ctx.spawnAnonymous(NodeActor(env, runtime))
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
