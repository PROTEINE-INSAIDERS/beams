package beams

import java.net.URLEncoder

import _root_.akka.actor.BootstrapSetup
import _root_.akka.actor.setup._
import _root_.akka.actor.typed._
import _root_.akka.actor.typed.receptionist._
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.actor.typed.scaladsl._
import scalaz.zio._

import scala.reflect.runtime.universe._

//TODO: factor out cluster interface abstraction (where possible)
package object akka extends HasSpawnProtocolSyntax with HasReceptionistSyntax {
  // А нам тут вообще нужен этот синоним?
  type Node[+Env] = NodeActor.Ref[Env]

  def serviceKey[R: TypeTag]: ServiceKey[BeamsSupervisor.Command[R]] = {
    val uid = URLEncoder.encode(typeTag[R].tpe.toString, "UTF-8")
    ServiceKey[BeamsSupervisor.Command[R]](s"beams-node-$uid")
  }

  /**
    * Create beams actor system.
    */
  def beamsNode[R: TypeTag](
                             systemName: String = "beams",
                             setup: ActorSystemSetup = ActorSystemSetup.create(BootstrapSetup()),
                             environment: ActorContext[BeamsSupervisor.Command[R]] => R
                           ): Managed[Throwable, BeamsSupervisor.Ref[R]] =
    Managed.make(IO {
      val system = ActorSystem(BeamsSupervisor(environment), systemName, setup)
      val key = serviceKey[R]
      system.receptionist ! Receptionist.Register(key, system)
      system
    })(tellZio(_, BeamsSupervisor.Shutdown))


  //TODO: мы не должны использовать встроенный паттерн ask (основанный на таймауте).
  // Нужно сделать собственный ask, поддерживающий "активную" остановку.

  // Для этого нужно создать актор, принимающий сигнал (Shutdown | Res)
  // и адаптер для него, конвертирующий Res -> (Shutdown | Res).

  private[akka] final case class AskZioPartiallyApplied[Res](dummy: Boolean = true) extends AnyVal {
    def apply[Req](ref: ActorRef[Req], replyTo: ActorRef[Res] => Req): TaskR[HasTimeLimit with HasScheduler, Res] = for {
      timeLimit <- ZIO.access[HasTimeLimit](_.timeLimit)
      scheduler <- ZIO.access[HasScheduler](_.scheduler)
      result <- IO.fromFuture { _ =>
        ref.ask[Res](replyTo)(timeLimit.current(), scheduler)
      }
    } yield result
  }

  private[akka] def askZio[Res]: AskZioPartiallyApplied[Res] = AskZioPartiallyApplied[Res]()

  private[akka] final case class AskZioPartiallyApplied1[Res](dummy: Boolean = true) extends AnyVal {
    def apply[Req](ref: ActorRef[Req], replyTo: ActorRef[Res] => Req) = {

      ZIO.runtime

      val aaa = for {
        promise <- Promise.make[Nothing, Res]
      } yield ???

      val bbb = for {
        pp <- accessSpawnProtocol
      } yield ???

      ???
    }
  }

  private[akka] def askZio1[Res]: AskZioPartiallyApplied1[Res] = AskZioPartiallyApplied1[Res]()

  private[akka] def tellZio[A](ref: ActorRef[A], a: A): Canceler = ZIO.effectTotal(ref.tell(a))
}
