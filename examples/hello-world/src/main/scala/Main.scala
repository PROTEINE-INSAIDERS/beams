import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import beams._
import beams.backend.akka._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scopt._
import zio._
import zio.console._

object Main extends App {
  private def nodeNames = Seq("Alice", "Bob", "Master")

  case class Config(serviceKey: String)

  private val parser: OParser[Unit, Config] = {
    val builder: OParserBuilder[Config] = OParser.builder[Config]
    import builder._
    OParser.sequence(
      programName("hello-world"),
      head("Beams hello-world example program", "0.1"),
      opt[String]('k', "serviceKey")
        .required()
        .validate { k =>
          if (Seq("Alice", "Bob", "Master").contains(k.capitalize)) {
            Right(())
          } else {
            Left("Service key should be either Alice, Bob or Master.")
          }
        }.action((k, c) => c.copy(serviceKey = k.capitalize))
        .text("Service key"))
  }

  private def setup(port: Int) = ActorSystemSetup(
    BootstrapSetup(ConfigFactory.load().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))))

  class NodeEnv(beam: Beam[AkkaBackend], val config: Config)
    extends Beam.Wrapper[AkkaBackend](beam) with Console.Live

  private def master = for {
    _ <- announce("Master")
    alice <- anyNode[NodeEnv]("Alice")
    _ <- at(alice) {
      ZIO.access[NodeEnv](_.config.serviceKey).flatMap(key => putStrLn(s"running at $key"))
    }
    bob <- anyNode[NodeEnv]("Bob")
    _ <- at(bob) {
      ZIO.access[NodeEnv](_.config.serviceKey).flatMap(key => putStrLn(s"running at $key"))
    }
  } yield ()

  private def slave = for {
    master <- anyNode[NodeEnv]("Master")
    _ <- ZIO.access[NodeEnv](_.config.serviceKey).flatMap(announce)
    _ <- deathwatch(master)
  } yield ()

  private def program(config: Config): Task[Unit] = for {
    setup <- IO(setup(9000 + nodeNames.indexOf(config.serviceKey)))
    result <- root(_.map(new NodeEnv(_, config)), setup = setup) {
      if (config.serviceKey == "Master") {
        master
      } else {
        slave
      }
    }
  } yield result

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    IO(OParser.parse(parser, args, Config(""))).flatMap {
      case Some(cfg) => program(cfg) *> ZIO.succeed(0)
      case None => ZIO.succeed(1)
    }.catchAll(error => IO.effectTotal(error.printStackTrace()) *> ZIO.succeed(1))
}
