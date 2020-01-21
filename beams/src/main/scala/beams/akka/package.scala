package beams

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import zio._

package object akka extends BeamsSyntax[AkkaBackend] {
  /**
   * Create beams root node.
   */
  //TODO: это создаст ActorSystem, которая затем может быть использована для создания Beam.
  // далее Beam может быть использован для создания рантайма и запуска задачи.
  //TODO: нужно понять, хотим ли мы использовать ZIO здесь. Возможно этот метод должен работать как unsafeRun...
  def root[R](f: Runtime[AkkaBeam] => Runtime[R], // Можно ли отказаться от этой функции, используя provide в клиентском коде?
              key: Option[NodeActor.Key[R]] = None,
              name: String = "beams",
              setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
              props: Props = Props.empty
             ): Managed[Throwable, Beam[AkkaBackend]] = Managed.make {
    for {
      system <- IO {
        val system = ActorSystem(NodeActor(f), name, setup, props)
        key.foreach(system.receptionist ! Receptionist.Register(_, system))
        system
      }
    } yield system
  } { system =>
    Task.effectTotal {
      system ! NodeActor.Stop
    }
  }.map(AkkaBeam(_))

}
