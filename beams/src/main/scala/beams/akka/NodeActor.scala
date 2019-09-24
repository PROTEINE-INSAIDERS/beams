package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams._
import scalaz.zio._

object NodeActor {
  type Ref[+Env] = ActorRef[Command[Env]]

  sealed trait Command[-Env] extends SerializableMessage

  final case class CreateNode[Env](env: Env, replyTo: ActorRef[Ref[Env]]) extends Command[Any]

  //TODO: можно добавить оптимизированную версию для локальной системы акторов, где вместо
  // replyTo будет использоваться Promise.
  final case class RunTask[Env, A](
                                    task: TaskR[Beam[Node, Env], A],
                                    replyTo: ActorRef[Exit[Throwable, A]],
                                    timeLimit: TimeLimitContainer
                                  ) extends Command[Env]

  object Stop extends Command[Any]

  def apply[Env](env: Env, runtime: Runtime[_]): Behavior[Command[Env]] = Behaviors.setup { ctx =>
    //val self = AkkaNode[Env](ctx.self)
    val self = ctx.self

    Behaviors.receiveMessagePartial {
      case RunTask(task, replyTo, TimeLimitContainer(timeLimit)) =>
        val beam = AkkaBeam[Env](self, env, timeLimit, ctx.system.scheduler)
        val program = task.provide(beam)
        runtime.unsafeRunAsync(program)(result => replyTo ! result)
        Behaviors.same

      case CreateNode(env, replyTo) =>
        replyTo ! ctx.spawnAnonymous(NodeActor(env, runtime), Props.empty)
        Behaviors.same

      case Stop =>
        Behaviors.stopped
    }
  }
}

object NodeActor1 {
  type Ref[+R] = ActorRef[Command[R]]

  sealed trait Command[-R] extends SerializableMessage

  // здесь мы можем предоставить функцию, которая примет контекст актора и создаст R
  def apply[R](): Behavior[Command[R]] = Behaviors.setup { ctx =>
    // при запуске процесса нужно создавать дочерний актор, способный принимать сообщение inerrupt.
    ???
  }
}
