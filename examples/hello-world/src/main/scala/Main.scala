import scopt.{OParser, OParserBuilder}
import zio._
import zio.console._

case class Config(serviceKey: Option[String])

trait HelloEnv extends Console {
  val config: Config
}

object Main extends App {
  val builder: OParserBuilder[Config] = OParser.builder[Config]

  val parser1: OParser[Unit, Config] = {
    import builder._
    OParser.sequence(
      programName("hello-world"),
      head("Beams hello-world example program", "0.1"),
      // option -f, --foo
      opt[String]('k', "serviceKey")
        .action((k, c) => c.copy(serviceKey = Some(k)))
        .text("Service key"))
  }

  val program: ZIO[HelloEnv, Throwable, Unit] = putStrLn("hello")

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    program.provideSomeM {
      for {
        z <- ZIO.environment[ZEnv]
      } yield new HelloEnv {
        override val config: Config = ???
        override val console: Console.Service[Any] = z.console
      }
    }.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
