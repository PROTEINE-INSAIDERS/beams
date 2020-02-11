package object beams {
  type Beam[X <: Backend] = Deathwatch[X] with Discovery[X] with Exclusive[X] with Execution[X] with NodeFactory[X]
}
