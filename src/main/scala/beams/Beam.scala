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

trait BeamTF[F[_]] {
  type Node

  def beamTo(node: Node): F[Unit]

  def nodes: F[NonEmptyList[Node]]
}

object Beam {
  def apply[F[_], N](implicit F: Beam[F, N]): Beam[F, N] = implicitly
}