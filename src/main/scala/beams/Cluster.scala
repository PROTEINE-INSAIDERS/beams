package beams

import cats.data.NonEmptyList
import simulacrum.typeclass

trait Cluster[F[_], Node] {
  def nodes: F[NonEmptyList[Node]]
}

object Cluster {
  def apply[F[_], Node](implicit F: Cluster[F, Node]): Cluster[F, Node] = implicitly
}