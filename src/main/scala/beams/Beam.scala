package beams

import cats.data.NonEmptyList

trait Beam[F[_]] extends Serializable {
  type Node

  def beamTo(node: Node): F[Unit]

  def nodes: F[NonEmptyList[Node]]
}

object Beam {
  def apply[F[_]](implicit F: Beam[F]): Beam[F] = implicitly
}