package beams.akka

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import beams.Beam
import scalaz.zio._

package object local {
  def createActorSystem(name: String): ActorSystem[SpawnProtocol.Command] = {
    ActorSystem(SpawnProtocol(), name)
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
