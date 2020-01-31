package beams.backend.akka.debug

import akka.actor.ExtendedActorSystem
import akka.serialization.BaseSerializer

import scala.collection.mutable

class FakeSerializer(val system: ExtendedActorSystem) extends BaseSerializer {
  private val BYTES = java.lang.Integer.BYTES
  private val objects: mutable.HashMap[Int, AnyRef] = new mutable.HashMap[Int, AnyRef]()

  override def toBinary(o: AnyRef): Array[Byte] = synchronized {
    var key = objects.size
    objects(key) = o
    val bytes = new Array[Byte](BYTES)
    for (i <- BYTES - 1 to 0 by -1) {
      bytes(i) = (key & 0xFF).toByte
      key = key >>> 8
    }
    bytes
  }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = synchronized {
    var key = 0
    for (i <- 0 until BYTES) {
      key = key << 8
      key = key | (bytes(i) & 0xFF)
    }
    val obj = objects(key)
    objects.remove(key)
    obj
  }
}
