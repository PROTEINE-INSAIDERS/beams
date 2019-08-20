package beams.akka

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import beams.Beam
import scalaz.zio._

package object local {
  /**
    * Create local actor system with spawn protocol for running tasks in current process.
    *
    * Usually there is no reasons to use beams in non-distributed applications thus this method primary usable for debugging.
    */
  def createActorSystem[Env](
                              env: Env,
                              name: String = "beams",
                              setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                              runtime: DefaultRuntime = new DefaultRuntime() {},
                            ): ActorSystem[SpawnNodeActor.Command[Env]] = {
    ActorSystem(SpawnNodeActor(env, runtime), name, setup)
  }

  def beam[Env, A](task: TaskR[Beam[AkkaNode, Env], A], system: ActorSystem[SpawnNodeActor.Command[Env]], timeout: TimeLimit): Task[A] = {
    implicit val t: TimeLimit = timeout
    implicit val s: Scheduler = system.scheduler

    Managed.make(askZio[NodeActor.Ref[Env]](system, SpawnNodeActor.Spawn[Env]))(tellZio(_, NodeActor.Stop)).use { root =>
      askZio[Exit[Throwable, Any]](root, NodeActor.RunTask(task.asInstanceOf[TaskR[Beam[AkkaNode, Env], A]], _, TimeLimitContainer(timeout, root)))
    }.flatMap(exit => IO.done(exit.asInstanceOf[Exit[Throwable, A]]))
  }
}
