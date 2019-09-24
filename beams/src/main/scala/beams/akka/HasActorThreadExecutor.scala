package beams.akka

import scalaz.zio.internal.Executor

/**
  * ZIO executor bounded to the actor's thread.
  * Used to invoke non thread-safe methods of actor context.
  */
trait HasActorThreadExecutor {
  def actorThreadExecutor: Executor
}
