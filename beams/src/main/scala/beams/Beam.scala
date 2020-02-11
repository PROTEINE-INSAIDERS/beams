package beams

import beams.pattern.MasterSlave

object Beam {

  class Wrapper[X <: Backend](b: Beam[X])
    extends Deathwatch[X]
      with Discovery[X]
      with Execution[X]
      with NodeFactory[X]
      with Exclusive[X] {
    final override def deathwatch: Deathwatch.Service[Any, X] = b.deathwatch

    final override def discovery: Discovery.Service[Any, X] = b.discovery

    final override def exclusive: Exclusive.Service[Any, X] = b.exclusive

    final override def execution: Execution.Service[Any, X] = b.execution

    final override def nodeFactory: NodeFactory.Service[Any, X] = b.nodeFactory
  }

  trait Syntax[X <: Backend]
    extends Deathwatch.Syntax[X]
      with Discovery.Syntax[X]
      with Exclusive.Syntax[X]
      with Execution.Syntax[X]
      with NodeFactory.Syntax[X]
      with MasterSlave.Syntax[X]

}
