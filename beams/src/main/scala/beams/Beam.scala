package beams

import zio._

trait Backend {
  type Node[+_]
}

trait Beam[X <: Backend] {
  def beam: Beam.Service[Any, X]
}

object Beam {

  //TODO: Подумать о декомпозиции сервиса. 
  // 1.
  trait Service[R, X <: Backend] {
    def announce(key: String): RIO[R, Unit]

    /**
     * Run task at specified node and waits for result.
     */
    def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[R, A]

    def deathwatch(node: X#Node[Any]): RIO[R, Unit]

    /**
     * List available nodes by given key.
     */
    def listing[U](key: String): RManaged[R, Queue[Set[X#Node[U]]]]

    /**
     * Create a child node which can run tasks.
     */
    //TODO: supervision??
    //TODO: startup task?
    def node[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[R, X#Node[U]]

    // TODO: Нужен ли task для singleton-а? По идее это удобно но:
    // 1. Мы не получим результата этой задачи, если передадим её в конструктор актора.
    // 2. Единовременный запуск можно выполнить и без этого.
    // 3. Мы не узнаем, нужно ли нам блокироваться и ожидать завершения выполения задачи, чтобы singleton
    //    node продолжала висеть в памяти.
    // Может быть нужен какой-нибудь mutex?
    // Возможно надо сделать exclusive[A](t: Task[A], key: String): RIO[R, Option[A]]
    // def singleton[U, A](f: Runtime[Beam[X]] => Runtime[U]): RIO[R, Unit]
    // Мы не контролируем, на каком узле будет запущен Cluster Singleton, следовательно не можем передать туда окружение.
    // Возможно, следует создать отдельный актор для синхронизации процессов.
    // и вообще вынести это из Beam.
  }

  class Wrapper[X <: Backend](b: Beam[X]) extends Beam[X] {
    override def beam: Service[Any, X] = b.beam
  }

}

trait BeamsSyntax[X <: Backend] extends Beam.Service[Beam[X], X] {
  override def announce(key: String): RIO[Beam[X], Unit] =
    RIO.accessM(_.beam.announce(key))

  override def at[U, A](node: X#Node[U])(task: RIO[U, A]): RIO[Beam[X], A] =
    RIO.accessM(_.beam.at(node)(task))

  override def deathwatch(node: X#Node[Any]): RIO[Beam[X], Unit] =
    RIO.accessM(_.beam.deathwatch(node))

  override def listing[U](key: String): RManaged[Beam[X], Queue[Set[X#Node[U]]]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.listing(key))

  override def node[U](f: Runtime[Beam[X]] => Runtime[U]): RManaged[Beam[X], X#Node[U]] =
    ZManaged.environment[Beam[X]].flatMap(_.beam.node(f))

//  override def singleton[U, A](f: Runtime[Beam[X]] => Runtime[U])(task: RIO[U, A]): RIO[Beam[X], Unit] =
//    RIO.accessM(_.beam.singleton(f)(task))

  /**
   * Wait till some nodes with given environment will become available.
   */
  def someNode[U](key: String): RIO[Beam[X], Set[X#Node[U]]] = ZIO.accessM { r =>
    r.beam.listing[U](key).use(_.take.repeat(Schedule.doUntil(_.nonEmpty)))
  }

  /**
   * Wait till any node with given environment will become available.
   */
  def anyNode[U](key: String): RIO[Beam[X], X#Node[U]] = someNode[U](key).map(_.head)
}