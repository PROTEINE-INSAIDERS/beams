package beams.akka

import akka.actor.NoSerializationVerificationNeeded
import scalaz.zio._

/**
  * Base trait for all beams's messages.
  * Can be used to set up custom serialization for beams.
  */
trait SerializableMessage extends Serializable


trait NonSerializableMessage extends NoSerializationVerificationNeeded

final case class ResultWrapper[A](exit: Exit[Throwable, A]) extends SerializableMessage