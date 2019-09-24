package beams.akka

import java.net.URLEncoder

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import beams.BeamSyntax
import scalaz.zio.{DefaultRuntime, _}

import scala.reflect.runtime.universe._

//TODO: zio-friendly.
// переменные окружения должны попадать в Task через ридер.
//TODO: перенести в akka.
// не имеет смысла выделять cluster отдельно, т.к. мы не работаем в не кластерном режиме.
package object cluster extends BeamSyntax[NodeActor.Ref] {
  //TODO: включение текушего узла в кластер.
  // 1. Создаём систему акторов.
  // 2. Cоздаём один или несколько узлов с заданным окружением.

  //TODO: создать и зарегистрировать ноду.
  def node[R](runtime: Runtime[R]): ZManaged[HasSpawnProtocol with HasReceptionist, Throwable, NodeActor1.Ref[R]] = {
    ???
  }

  //TODO: нужен также метод для подписки
  def rootNodes[Env: TypeTag](implicit system: ActorSystem[SpawnProtocol.Command]) = ???

  //TODO: возможно это следует перенести в интерфейс Beam (или Cluster).
  // с другой стороны конкретно этот интерфейс используется для ожидания готовности кластера перед запуском beam-а.
  //TODO: Переименовать. Суффикс Listing пришел из названия трейта, которого тут даже нет.
  /*
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
   */

  //TODO: rename to toTask?
  //TODO: запускаться на Task[SpawnNodeActor.Ref[Env]]
  //TODO: может быть вообще в forkto переименовать.
  /*
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
   */
}
