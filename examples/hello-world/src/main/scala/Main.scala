import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import beams._
import beams.akka._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scopt._
import zio._
import zio.console._

case class Config(serviceKey: String)

trait NodeEnv extends Beam[AkkaBackend] with Console

object Main extends App {
  private def nodeNames = Seq("Alice", "Bob", "Master")

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

  private def nodeEnv(beamEnv: Beam[AkkaBackend]): NodeEnv = new NodeEnv {
    override val console: Console.Service[Any] = Console.Live.console

    override def beam: Beam.Service[Any, AkkaBackend] = beamEnv.beam
  }

  private def slave(k: String): RIO[NodeEnv, Unit] = for {
    master <- key[NodeEnv]("Master") >>= anyNode
  } yield ()

  private def master(k: String): RIO[NodeEnv, Unit] = for {
    alice <- key[NodeEnv]("Alice") >>= anyNode
    _ <- at(alice)(ZIO.succeed(1))
    bob <- key[NodeEnv]("Bob") >>= anyNode
    _ <- at(bob)(ZIO.succeed(2))
  } yield ()

  private def program(config: Config): Task[Unit] = for {
    setup <- IO(setup(9000 + nodeNames.indexOf(config.serviceKey)))
    result <- root(_.map(nodeEnv), setup = setup) {
      if (config.serviceKey == "Master") {
        ???
      } else {
        ???
      }
    }
  } yield result

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (IO {
      OParser.parse(parser, args, Config(""))
    } >>= {
      case Some(cfg) => program(cfg) *> ZIO.succeed(0)
      case None => ZIO.succeed(1)
    }).catchAll(error => putStrLn(error.toString) *> ZIO.succeed(1))
}
