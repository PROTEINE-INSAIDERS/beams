import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import beams.akka.cluster._
import beams.akka.{FixedTimeout, SpawnNodeActor, TimeLimit}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._
import scalaz.zio.console._
import scalaz.zio.stream._

import scala.concurrent.duration._

object Main extends App {
  private def setup(port: Int): ActorSystemSetup = ActorSystemSetup(BootstrapSetup(
    ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))))

  def program: TaskR[Console, Unit] = {
    implicit val limit: TimeLimit = FixedTimeout(30 seconds)
    implicit val runtime: DefaultRuntime = this

    for {
      system1 <- createActorSystem(setup = setup(25520))
      system2 <- createActorSystem(setup = setup(25521))
      system3 <- createActorSystem(setup = setup(25522))
      _ <- registerRoot("node1", system1)
      _ <- registerRoot("node2", system2)
      _ <- registerRoot("node3", system3)
      _ <- rootNodesListing[String](system1).use { queue =>
        for {
          _ <- putStrLn(s"==== Wait queue $queue")
          _ <- getStrLn
          // _ <- Stream.fromQueue(queue).foreach(a => putStrLn(s"===== $a"))
          /*
          _ <- Stream.fromQueue(queue).run(Sink.ignoreWhileM[Console, Throwable, Set[SpawnNodeActor.Ref[String]]]{a =>
            for {
              _ <- putStrLn(s"==== $a")
            } yield  a.size < 3
          })
          */
        } yield ()
      }
      _ <- getStrLn
    } yield ()
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
