package beams

import zio._

trait Exclusive[X <: Backend] {
  def exclusive: Exclusive.Service[Any, X]
}

object Exclusive {

  trait Service[R, X <: Backend] {
    /**
     * Run task exclusively on current node. If task with the same key is already running on any node
     * method will return None immediately or will run the task and return it result.
     */
    //TODO: а нужно ли нам иметь доступ к результату задачи, если она рантинся на другом узле?
    def exclusive[A, R1 <: R](key: String)(task: RIO[R1, A]): RIO[R1, Option[A]]
  }

  trait Syntax[X <: Backend] extends Service[Exclusive[X], X] {
    final override def exclusive[A, R1 <: Exclusive[X]](key: String)(task: RIO[R1, A]): RIO[R1, Option[A]] =
      RIO.accessM(_.exclusive.exclusive(key)(task))
  }

}
