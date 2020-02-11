package beams

import zio.{RManaged, Runtime, ZManaged}

trait NodeFactory[X <: Backend] {
  def nodeFactory: NodeFactory.Service[Any, X]
}

object NodeFactory {

  trait Service[R, X <: Backend] {
    /**
     * Create a child node which can run tasks.
     */
    def node[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[R, X#Node[U]]
  }

  trait Syntax[X <: Backend] extends Service[NodeFactory[X], X] {
    final override def node[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[NodeFactory[X], X#Node[U]] =
      ZManaged.environment[NodeFactory[X]].flatMap(_.nodeFactory.node(f))
  }

}
