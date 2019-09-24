package beams.akka

trait RequiresActorThread {
  def blockActorThread()

  def releaseActorThread()
}
