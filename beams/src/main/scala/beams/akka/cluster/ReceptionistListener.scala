package beams.akka.cluster

import akka.actor.typed._
import akka.actor.typed.receptionist._
import akka.actor.typed.scaladsl._
import beams.akka._
import scalaz.zio._

object ReceptionistListener {

  type Ref = ActorRef[Command]

  sealed trait Command extends NonSerializableMessage

  final case class Register[ServiceCommand](
                                             key: ServiceKey[ServiceCommand],
                                             queue: Queue[Set[ActorRef[ServiceCommand]]],
                                             limit: TimeLimit,
                                             replyTo: ActorRef[ActorRef[Receptionist.Listing]]
                                           ) extends Command

  object Stop extends Command

  private def listener[ServiceCommand](
                                        key: ServiceKey[ServiceCommand],
                                        queue: Queue[Set[ActorRef[ServiceCommand]]],
                                        limit: TimeLimit,
                                        runtime: DefaultRuntime
                                      ): Behavior[Receptionist.Listing] = Behaviors.setup { ctx =>
    implicit val l: TimeLimit = limit
    implicit val s: Scheduler = ctx.system.scheduler

    Behaviors
      .receiveMessagePartial[Receptionist.Listing] {
        case key.Listing(services) =>
          runtime.unsafeRun(queue.offer(services))
          Behaviors.same
      }
      .receiveSignal {
        case (_, PostStop) =>
          queue.shutdown
          Behaviors.same
      }
  }


  def apply[ServiceCommand](runtime: DefaultRuntime): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessagePartial {
        case Register(key, queue, limit, replyTo) =>
          replyTo ! ctx.spawnAnonymous(listener(key, queue, limit, runtime))
          Behaviors.same
        case Stop =>
          Behaviors.stopped
      }
    }
}
