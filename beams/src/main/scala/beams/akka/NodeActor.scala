package beams.akka
/*
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams.Beam
import scalaz.zio._
import scalaz.zio.internal._

import scala.collection.mutable

/**
  * Top-level actor for a beams cluster's node.
  */
object NodeActor {
  type Ref[+R] = ActorRef[Command[R]]

  type Ctx[R] = ActorContext[Command[R]]

  //TODO: добавить тэги и использовать scala.annotation.switch
  sealed trait Command[-R]

  //TODO: Возможно в целях оптимизации следует добавить сообщение, предназначенное для локального запуска задач.
  // replyTo в нём следует заменить call-back функцией.
  // Это позволит избежать создания доплнительных акторов для реализации ask-паттернов.
  private[akka] final case class Exec[R, A](
                                             task: TaskR[R, A],
                                             replyTo: ActorRef[ResultWrapper[A]]
                                           ) extends Command[R] with SerializableMessage

  //TODO: Mожно обойтись без этого сообщения. Для этого достаточно создавать фиберы через unsafeRun.
  // Отмену регистрации в любом случае придётся делать через сообщения, чтобы обеспечить потокобезопасность.
  private final case class RegisterFiber(fiber: Fiber[_, _], initiator: ActorRef[_], done: Task[Unit] => Unit) extends Command[Any] with NonSerializableMessage

  private final case class RegisterOrphan(orphan: Fiber[_, _], done: Task[Int] => Unit) extends Command[Any] with NonSerializableMessage

  private final case class UnregisterFiber(initiator: ActorRef[_]) extends Command[Any] with NonSerializableMessage

  private final case class UnregisterOrphan(id: Int) extends Command[Any] with NonSerializableMessage

  private[akka] final case class AccessActorContext(f: ActorContext[_] => Unit) extends Command[Any] with NonSerializableMessage

  private[akka] final case class Submit[R](task: TaskR[R, Any]) extends Command[R] with SerializableMessage

  private[akka] final case class Interrupt(initiator: ActorRef[_]) extends Command[Any] with SerializableMessage

  //TODO: need to implement gracefull shutdown (wait till all tasks finished or canceled shut down node, invoke call-back function).
  // No remote shutdown required
  private[akka] object Stop extends Command[Any] with NonSerializableMessage

  // scalastyle:off cyclomatic.complexity
  private[akka] def apply[R](f: Beam[NodeActor.Ref] => R): Behavior[Command[R]] =
    Behaviors.setup { ctx =>
      val runtime = Runtime(f(new AkkaBeam(ctx.self)), PlatformLive.fromExecutionContext(ctx.executionContext))
      val fibers = mutable.HashMap[ActorRef[_], Fiber[_, _]]()
      val orphans = mutable.HashMap[Int, Fiber[_, _]]()

      Behaviors.receiveMessagePartial {
        case Exec(task, replyTo) =>
          val register = for {
            fiber <- task.fork
            _ <- Task.effectAsync { (cb: Task[Unit] => Unit) => ctx.self.tell(RegisterFiber(fiber, replyTo, cb)) }
          } yield fiber
          val unregister = tellZIO(ctx.self, UnregisterFiber(replyTo))
          runtime.unsafeRunAsync(register.bracket(_ => unregister)(_.join))(r => replyTo.tell(ResultWrapper(r)))
          Behaviors.same
        case RegisterFiber(fiber, initiator, done) =>
          fibers += initiator -> fiber
          done(Task.succeed(()))
          Behaviors.same
        case RegisterOrphan(orphan, done) =>
          val id = orphans.size
          orphans += (id -> orphan)
          done(Task.succeed(id))
          Behaviors.same
        case UnregisterFiber(initiator) =>
          fibers -= initiator
          Behaviors.same
        case UnregisterOrphan(id) =>
          orphans -= id
          Behaviors.same
        case AccessActorContext(f) =>
          f(ctx)
          Behaviors.same
        case Submit(task) =>
          val register = for {
            fiber <- task.fork
            id <- Task.effectAsync { (cb: Task[Int] => Unit) => ctx.self.tell(RegisterOrphan(fiber, cb)) }
          } yield (fiber, id)
          val unregister = (id: Int) => tellZIO(ctx.self, UnregisterOrphan(id))
          runtime.unsafeRunAsync_(register.bracket { case (_, id) => unregister(id) } { case (fiber, _) => fiber.join })
          Behaviors.same
        case Interrupt(initiator) =>
          fibers.get(initiator).foreach { fiber =>
            runtime.unsafeRun(fiber.interrupt)
            fibers -= initiator
          }
          Behaviors.same
        case Stop =>
          //TODO: возможно, некоторым фиберам для завершения работы потребуется обратиться к ноде,
          // поэтому прерывать их надо до завершения работы ноды.
          // Можно реализовать двухфазное отключение: в первой фазе нода просто перестаёт принимать новые задачи,
          // но продолжает обслуживать еще существующие, когда все задачи выполнены, нода переходит в состояние Behaviors.stopped.
          Behaviors.stopped { () =>
            def interruptAll(fibers: Iterable[Fiber[_, _]]): Unit = fibers.foreach(fiber => runtime.unsafeRunAsync_(fiber.interrupt))

            interruptAll(fibers.values)
            interruptAll(orphans.values)
          }
      }
    }

  // scalastyle:on cyclomatic.complexity
}
*/