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

import scala.reflect.runtime.universe._

package object cluster extends BeamSyntax[AkkaNode] {
  def serviceKey[Env: TypeTag]: ServiceKey[RootNodeActor.Command[Env]] = {
    val uid = URLEncoder.encode(typeTag[Env].tpe.toString, "UTF-8")
    ServiceKey[RootNodeActor.Command[Env]](s"beam-$uid")
  }

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
                                  system: ActorSystem[SpawnProtocol.Command]
                                )
                                (
                                  implicit timeLimit: TimeLimit, runtime: DefaultRuntime
                                ): Task[RootNodeActor.Ref[Env]] = {
    implicit val s: Scheduler = system.scheduler

    for {
      key <- Task(serviceKey[Env])
      root <- askZio[RootNodeActor.Ref[Env]](system, SpawnProtocol.Spawn(RootNodeActor(env, runtime), key.id, Props.empty, _))
      _ <- tellZio(system.receptionist, Receptionist.Register(key, root))
    } yield root
  }

  //TODO: возможно это следует перенести в интерфейс Beam (или Cluster).
  // с другой стороны конкретно этот интерфейс используется для ожидания готовности кластера перед запуском beam-а.
  def rootNodeListing[Env: TypeTag](
                                      system: ActorSystem[SpawnProtocol.Command]
                                    )
                                   (
                                      implicit timeLimit: TimeLimit, runtime: DefaultRuntime
                                    ): Managed[Throwable, Queue[Set[RootNodeActor.Ref[Env]]]] = {
    Managed.make {
      implicit val s: Scheduler = system.scheduler
      for {
        key <- Task(serviceKey[Env])
        queue <- scalaz.zio.Queue.unbounded[Set[RootNodeActor.Ref[Env]]]
        guard <- askZio[ReceptionistListener.Ref](system, SpawnProtocol.Spawn(ReceptionistListener(runtime), "", Props.empty, _))
        listener <- askZio[ActorRef[Receptionist.Listing]](guard, ReceptionistListener.Register(key, queue, timeLimit, _))
        _ <- tellZio(system.receptionist, Receptionist.subscribe(key, listener))
      } yield {
        (queue, guard)
      }
    } { case (_, listener) =>
      ZIO.effectTotal(listener ! ReceptionistListener.Stop)
    } map (_._1)
  }

  //TODO: rename to toTask?
  //TODO: запускаться на Task[SpawnNodeActor.Ref[Env]]
  //TODO: может быть вообще в forkto переименовать.
  def beam[Env: TypeTag, A](
                             task: TaskR[Beam[AkkaNode, Env], A],
                             system: ActorSystem[Nothing],
                             timeLimit: TimeLimit
                           ): Task[A] = {
    val key = serviceKey[Env]
    implicit val t: TimeLimit = timeLimit
    implicit val s: Scheduler = system.scheduler

    for {
      listing <- askZio[Listing](system.receptionist, replyTo => Receptionist.Find(key, replyTo))
      exit <- Managed.collectAll(
        listing.serviceInstances(key).map(spawn => Managed.make(askZio[NodeActor.Ref[Env]](spawn, RootNodeActor.CreateNode[Env]))(tellZio(_, NodeActor.Stop))))
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
