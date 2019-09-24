package beams.akka

import akka.actor.typed.scaladsl.ActorContext

//TODO: так как методы actorContext в основном требуют синхронного исполнения,
// данный трейт следует заменить механизмом синхронного запуска задач в контексте актора.
trait HasActorContext {
  def actorContext: ActorContext[_]
}