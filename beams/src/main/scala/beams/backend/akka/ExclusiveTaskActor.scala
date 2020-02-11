package beams.backend.akka

import akka.actor.typed.Behavior

private[akka] object ExclusiveTaskActor {

  trait Command

  /**
   * Execute local task exclusively.
   *
   * This actor will also watch thie {@} and interrupt the task in case of
   */
  def apply(exclusiveActor: ExclusiveActor.Ref): Behavior[Command] = ???
}
