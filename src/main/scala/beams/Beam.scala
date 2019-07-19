package beams

trait Beam[F[_], Node] {
  def beamTo(node: Node): F[Unit]
}