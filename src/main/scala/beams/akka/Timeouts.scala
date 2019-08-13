package beams.akka

import _root_.akka.util._

import scala.concurrent.duration.FiniteDuration

/**
  * Timeout strategy which can be used on local host.
  */
trait LocalTimeout {
  /**
    * Get current timeout value for akka timed operations.
    */
  def current(): Timeout

  /**
    * Create timeout strategy which can be migrated to remote host.
    */
  def migratable(): MigratableTimeout
}

/**
  * Timeout strategy which can be moved between hosts.
  */
trait MigratableTimeout {
  /**
    * Create local version of timeout strategy which can be used to obtain timeout values.
    */
  def local(): LocalTimeout
}

final case class FixedTimeout(timeout: Timeout) extends LocalTimeout with MigratableTimeout {
  def apply(duration: FiniteDuration): FixedTimeout = FixedTimeout(Timeout(duration))
  
  override def current(): Timeout = timeout

  override def migratable(): MigratableTimeout = this

  override def local(): LocalTimeout = this
}