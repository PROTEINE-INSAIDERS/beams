package beams

trait Backend {
  type Node[+ _]
  type Key[_]
}
