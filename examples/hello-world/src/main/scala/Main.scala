import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import beams._
import beams.akka._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scopt._
import zio._
import zio.console._

case class Config(serviceKey: String)

trait AppEnv extends Console {
  val config: Config
}

trait NodeEnv extends Beam[AkkaBackend] with Console {
}

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

  private val program: ZIO[AppEnv, Throwable, Unit] = for {
    env <- ZIO.access[AppEnv](_.config)
    _ <- putStrLn(s"I am ${env.serviceKey}.")
    k <- key[AppEnv](env.serviceKey)
    _ <- root(_.map(b => new AppEnv {
      override val config: Config = env.copy()
      override val console: Console.Service[Any] = Console.Live.console
    }), Some(k), setup = setup(9000 + nodeNames.indexOf(env.serviceKey))) {
      ???
    }
  } yield ()

  private def setup(port: Int) = ActorSystemSetup(
    BootstrapSetup(ConfigFactory.load().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))))


  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (IO {
      OParser.parse(parser, args, Config(""))
    } >>= {
      case Some(cfg) => program.provideSome[ZEnv](z => new AppEnv {
        override val config: Config = cfg
        override val console: Console.Service[Any] = z.console
      }) *> ZIO.succeed(0)
      case None => ZIO.succeed(1)
    }).catchAll(error => putStrLn(error.toString) *> ZIO.succeed(1))
}
