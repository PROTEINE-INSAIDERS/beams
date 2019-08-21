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

import scala.concurrent.duration._

package object cluster extends BeamSyntax[AkkaNode] {
  def defaultServiceKey[Env]: ServiceKey[SpawnNodeActor.Command[Env]] = ServiceKey[SpawnNodeActor.Command[Env]]("beams-root-node")

  val defaultTimeLimit = FixedTimeout(30 seconds)

  /**
    * Create beams actor system and join the cluster.
    */
  def createActorSystem[Env](
                              env: Env,
                              name: String = "beams",
                              setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                              runtime: DefaultRuntime = new DefaultRuntime() {},
                              beamsKey: ServiceKey[SpawnNodeActor.Command[Env]] = defaultServiceKey[Env]
                            ): ActorSystem[Nothing] = {
    val untypedSystem = akka.actor.ActorSystem(name, setup)
    val system = untypedSystem.toTyped
    val rootNode = untypedSystem.spawn(SpawnNodeActor(env, runtime), beamsKey.id)
    system.receptionist ! Receptionist.Register(beamsKey, rootNode)
    system
  }

  //TODO: rename to toTask?
  def beam[Env, A](
                    task: TaskR[Beam[AkkaNode, Env], A],
                    system: ActorSystem[Nothing],
                    timeLimit: TimeLimit = defaultTimeLimit,
                    beamsKey: ServiceKey[SpawnNodeActor.Command[Env]] = defaultServiceKey[Env]
                  ): Task[A] = {
    implicit val t: TimeLimit = timeLimit
    implicit val s: Scheduler = system.scheduler

    for {
      listing <- askZio[Listing](system.receptionist, replyTo => Receptionist.Find(beamsKey, replyTo))
      exit <- Managed.collectAll(
        listing.serviceInstances(beamsKey).map(spawn => Managed.make(askZio[NodeActor.Ref[Env]](spawn, SpawnNodeActor.Spawn[Env]))(tellZio(_, NodeActor.Stop))))
        .use { nodes =>
          Task.fromFuture { _ =>
            val master = nodes.find(_.path.address.hasLocalScope).orElse(nodes.headOption).getOrElse(throw new Exception("No nodes to run the program!"))
            implicit val timeout: Timeout = timeLimit.current()
            master.ask[Exit[Throwable, A]](NodeActor.RunTask(task, _, TimeLimitContainer(timeLimit, master)))
          }
        }
      result <- IO.done(exit)
    }
      yield result
  }
}
