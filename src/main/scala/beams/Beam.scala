package beams

import cats.data.NonEmptyList

trait Beam[F[_], Node] {
  def beamTo(node: Node): F[Unit]

  /**
    * Create new distributed I-Var.
    * @return read and write methods.
    */
  def newIVar[A]: F[(F[A], A => F[Unit])]

  def nodes: F[NonEmptyList[Node]]
}

object Beam {
  def apply[F[_], N](implicit F: Beam[F, N]): Beam[F, N] = implicitly
}

trait BeamTF[F[_]] extends Serializable {
  type Node

  def beamTo(node: Node): F[Unit]

  def nodes: F[NonEmptyList[Node]]
}

object BeamTF {
  def apply[F[_]](implicit F: BeamTF[F]): BeamTF[F] = implicitly
}