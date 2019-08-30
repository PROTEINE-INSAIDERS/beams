package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import scalaz.zio._

// SpawnNodeActor используется чтобы генерировать корневые узлы на нодах при запуске процесса. Это облегчает управление
// ресурсами (при завершении процесса достаточно остановить все сгенерированные для него узлы).
// На самом деле эта парадигма не очень хорошо поддерживает динамическое включение/исключение узлов кластера,
// возможно её следует пересмотреть.
object RootNodeActor {
  type Ref[+Env] = ActorRef[Command[Env]]

  sealed trait Command[-Env] extends SerializableMessage

  final case class CreateNode[Env](replyTo: ActorRef[NodeActor.Ref[Env]]) extends Command[Env]

  object Stop extends Command[Nothing]

  def apply[Env](env: Env, runtime: DefaultRuntime): Behavior[Command[Env]] = Behaviors.setup { ctx =>
    Behaviors.receiveMessagePartial {
      case CreateNode(replyTo) =>
        replyTo ! ctx.spawnAnonymous(NodeActor(env, runtime))
        Behaviors.same
      case Stop =>
        Behaviors.stopped
    }
  }
}
