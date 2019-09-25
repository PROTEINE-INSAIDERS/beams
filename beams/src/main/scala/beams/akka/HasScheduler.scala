package beams.akka

import akka.actor.typed.Scheduler

trait HasScheduler {
  def scheduler: Scheduler
}
