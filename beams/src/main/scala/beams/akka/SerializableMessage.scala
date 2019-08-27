package beams.akka

/**
  * Base trait for all beams's messages.
  * Can be used to set up custom serialization for beams.
  */
trait SerializableMessage extends Serializable

trait NonSerializableMessage
