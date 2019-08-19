package beams.akka

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import beams.Beam
import scalaz.zio._

//TODO: возможно для единообразия следует переработать АPI - createActorSystem должен принимать параметр Env
// и создавать ActorSystem[NodeActor.Ref] с данным Env.
package object local {
  /**
    * Create local actor system with spawn protocol for running tasks in current process.
    *
    * Usually there is no reasons to use beams in non-distributed applications thus this method primary usable for debugging.
    */
  def createActorSystem(name: String = "beams", setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup())): ActorSystem[SpawnProtocol.Command] = {
    ActorSystem(SpawnProtocol(), name, setup)
  }

  def beam[A](task: TaskR[Beam[AkkaNode, Any], A], system: ActorSystem[SpawnProtocol.Command], timeout: LocalTimeout): Task[A] = {
    Managed.make(IO.fromFuture { implicit ctx =>
      implicit val ct: Timeout = timeout.current()
      implicit val sch: Scheduler = system.scheduler
      system.ask[NodeActor.Ref](SpawnProtocol.Spawn(NodeActor(()), "", Props.empty, _))
    })(r => ZIO.effectTotal(r.tell(NodeActor.Stop))).use { root =>
      IO.fromFuture { implicit ctx =>
        implicit val ct: Timeout = timeout.current()
        implicit val sch: Scheduler = system.scheduler
        root.ask[Exit[Throwable, Any]](replyTo => NodeActor.RunTask(task, replyTo, timeout.migratable()))
      }
    }.flatMap(exit => IO.done(exit.asInstanceOf[Exit[Throwable, A]]))
  }
}
