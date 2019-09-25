package beams.akka

import akka.actor.typed.scaladsl.ActorContext
import scalaz.zio._

trait ActorContextAccessor {
  def actorContextAccessor: ActorContextAccessor.Service[Any]
}

object ActorContextAccessor {

  trait Service[R] {
    def withActorContext[A](f: ActorContext[_] => A): TaskR[R, A]
  }

}

trait ActorContextAccessorSyntax extends ActorContextAccessor.Service[ActorContextAccessor] {
  override def withActorContext[A](f: ActorContext[_] => A): TaskR[ActorContextAccessor, A] = ZIO.accessM {
    _.actorContextAccessor.withActorContext(f)
  }
}