package beams.akka

import java.net.URLEncoder

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import beams.{Beam, BeamSyntax}
import scalaz.zio._

import scala.concurrent.duration._
import scala.reflect.runtime.universe._

package object cluster extends BeamSyntax[AkkaNode] {
  def serviceKey[Env: TypeTag]: ServiceKey[SpawnNodeActor.Command[Env]] = {
    val uid = URLEncoder.encode(typeTag[Env].tpe.toString, "UTF-8")
    ServiceKey[SpawnNodeActor.Command[Env]](s"beam-$uid")
  }

  private val defaultTimeLimit = FixedTimeout(30 seconds)

  /**
    * Create beams actor system and join the cluster.
    */
  def createActorSystem(
                         name: String = "beams",
                         setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup())
                       ): Task[ActorSystem[SpawnProtocol.Command]] = Task {
    ActorSystem(SpawnProtocol(), name, setup)
  }

  def registerRoot[Env: TypeTag](
                                  env: Env,
                                  system: ActorSystem[SpawnProtocol.Command],
                                  runtime: DefaultRuntime = new DefaultRuntime() {},
                                )
                                (implicit timeLimit: TimeLimit = defaultTimeLimit): Task[SpawnNodeActor.Ref[Env]] = {
    implicit val s: Scheduler = system.scheduler

    for {
      key <- Task(serviceKey[Env])
      root <- askZio[SpawnNodeActor.Ref[Env]](system, SpawnProtocol.Spawn(SpawnNodeActor(env, runtime), key.id, Props.empty, _))
      _ <- tellZio(system.receptionist, Receptionist.Register(key, root))
    } yield root
  }

  def listingRoot[Env: TypeTag](system: ActorSystem[SpawnProtocol.Command])
                               (implicit timeLimit: TimeLimit = defaultTimeLimit): Task[Queue[SpawnNodeActor.Ref[Env]]] = {
    implicit val s: Scheduler = system.scheduler
    for {
      queue <- scalaz.zio.Queue.unbounded[Set[SpawnNodeActor.Ref[Env]]]
      listener <- tellZio(system.receptionist, ListenReceptionistActor(serviceKey[Env], queue))
    } yield queue
  }

  //TODO: rename to toTask?
  def beam[Env: TypeTag, A](
                             task: TaskR[Beam[AkkaNode, Env], A],
                             system: ActorSystem[Nothing],
                             timeLimit: TimeLimit = defaultTimeLimit
                           ): Task[A] = {
    val key = serviceKey[Env]
    implicit val t: TimeLimit = timeLimit
    implicit val s: Scheduler = system.scheduler

    for {
      listing <- askZio[Listing](system.receptionist, replyTo => Receptionist.Find(key, replyTo))
      exit <- Managed.collectAll(
        listing.serviceInstances(key).map(spawn => Managed.make(askZio[NodeActor.Ref[Env]](spawn, SpawnNodeActor.Spawn[Env]))(tellZio(_, NodeActor.Stop))))
        .use { nodes =>
          Task.fromFuture { _ =>
            val master = nodes.find(_.path.address.hasLocalScope).orElse(nodes.headOption).getOrElse {
              throw new Exception(s"Unable to find any reachable node with a `${typeTag[Env].tpe}' environment.")
            }
            implicit val timeout: Timeout = timeLimit.current()
            master.ask[Exit[Throwable, A]](NodeActor.RunTask(task, _, TimeLimitContainer(timeLimit, master)))
          }
        }
      result <- IO.done(exit)
    }
      yield result
  }
}
