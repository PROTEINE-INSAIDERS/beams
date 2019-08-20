package beams.akka

import _root_.akka.util._
import akka.actor.typed.ActorRef

import scala.concurrent.duration.FiniteDuration

/**
  * Timeout strategy which can be used on local host.
  */
trait TimeLimit {
  /**
    * Get current timeout value for akka timed operations.
    */
  def current(): Timeout

  /**
    * Create timeout strategy which can be migrated to remote host.
    */
  def migratable(): MigratableTimeLimit
}

/**
  * Timeout strategy which can be moved between hosts.
  */
trait MigratableTimeLimit extends Serializable {
  /**
    * Create local version of timeout strategy which can be used to obtain timeout values.
    */
  def local(): TimeLimit
}

sealed trait TimeLimitContainer extends Serializable

final case class LocalTimeLimitContainer(timeLimit: TimeLimit) extends TimeLimitContainer

final case class MigratableTimeLimitContainer(timeLimit: MigratableTimeLimit) extends TimeLimitContainer

object TimeLimitContainer {
  def apply(limit: TimeLimit, destination: ActorRef[_]): TimeLimitContainer = {
    if (destination.path.address.hasLocalScope) {
      LocalTimeLimitContainer(limit)
    } else {
      MigratableTimeLimitContainer(limit.migratable())
    }
  }

  def unapply(arg: TimeLimitContainer): Option[TimeLimit] = arg match {
    case LocalTimeLimitContainer(limit) => Some(limit)
    case MigratableTimeLimitContainer(limit) => Some(limit.local())
  }
}

final case class FixedTimeout(timeout: Timeout) extends TimeLimit with MigratableTimeLimit {
  def apply(duration: FiniteDuration): FixedTimeout = FixedTimeout(Timeout(duration))

  override def current(): Timeout = timeout

  override def migratable(): MigratableTimeLimit = this

  override def local(): TimeLimit = this
}
