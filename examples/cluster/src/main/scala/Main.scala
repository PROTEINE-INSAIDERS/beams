import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import beams.akka.cluster._
import beams.akka.{FixedTimeout, RootNodeActor, TimeLimit}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._
import scalaz.zio.console._
import scalaz.zio.stream._

import scala.concurrent.duration._

//TODO: Мониторинг регистрации: при регистрации ноды определённого типа мы хотим выполнить на ней определённую программy
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
      _ <- putStrLn("Waiting for 2 nodes...")
      _ <- rootNodeListing[String](system1).use(Stream.fromQueue(_).run(Sink.ignoreWhile[Set[RootNodeActor.Ref[String]]](_.size < 2)))
      _ <- registerRoot("node3", system3)
      _ <- putStrLn("Done.")
      _ <- getStrLn
    } yield ()
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
