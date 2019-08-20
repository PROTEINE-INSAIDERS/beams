package beams.akka

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import beams.{Beam, BeamSyntax}
import scalaz.zio._

package object cluster extends BeamSyntax[AkkaNode] {
  val defaultServiceKey: ServiceKey[SpawnNodeActor.Command] = ServiceKey[SpawnNodeActor.Command]("beams-root-node")

  /**
    * Create beams actor system and join the cluster.
    */
  def createActorSystem[Env](
                              env: Env,
                              name: String = "beams",
                              setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                              runtime: DefaultRuntime = new DefaultRuntime() {},
                              beamsKey: ServiceKey[SpawnNodeActor.Command] = defaultServiceKey
                            ): ActorSystem[Nothing] = {
    val untypedSystem = akka.actor.ActorSystem(name, setup)
    val system = untypedSystem.toTyped
    val rootNode = untypedSystem.spawn(SpawnNodeActor(env, runtime), beamsKey.id)
    system.receptionist ! Receptionist.Register(beamsKey, rootNode)
    system
  }

  //TODO: rename to toTask?
  def beam[A](
               task: TaskR[Beam[AkkaNode, Any], A],
               system: ActorSystem[Nothing],
               timeLimit: TimeLimit,
               beamsKey: ServiceKey[SpawnNodeActor.Command] = defaultServiceKey
             ): Task[A] = {
    implicit val t: TimeLimit = timeLimit
    implicit val s: Scheduler = system.scheduler

    for {
      listing <- askZio[Listing](system.receptionist, replyTo => Receptionist.Find(beamsKey, replyTo))
      exit <- Managed.collectAll(
        listing.serviceInstances(beamsKey).map(spawn => Managed.make(askZio[NodeActor.Ref](spawn, SpawnNodeActor.Spawn))(tellZio(_, NodeActor.Stop))))
        .use { nodes =>
          Task.fromFuture { _ =>
            val master = nodes.find(_.path.address.hasLocalScope).orElse(nodes.headOption).getOrElse(throw new Exception("No nodes to run the program!"))
            implicit val timeout: Timeout = timeLimit.current()
            master.ask[Exit[Throwable, Any]](NodeActor.RunTask(task.asInstanceOf[TaskR[Beam[AkkaNode, Any], A]], _, TimeLimitContainer(timeLimit, master)))
          }
        }
      result <- IO.done(exit.asInstanceOf[Exit[Throwable, A]])
    }
      yield result
  }
}
