package beams.akka

import scalaz.zio.internal._

//TODO: Это всё можно схлопнуть в один трейт.
trait BeamsEnvironment extends HasActorContext with HasActorThreadExecutor with RequiresActorThread {
  private val singleThreadExecutor = SingleThreadExecutor()

  override def actorThreadExecutor: Executor = singleThreadExecutor

  override def blockActorThread(): Unit = singleThreadExecutor.enterLoop()

  override def releaseActorThread(): Unit = singleThreadExecutor.stop()
}
