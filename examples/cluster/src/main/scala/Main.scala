import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed.scaladsl.ActorContext
import beams.akka._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._
import scalaz.zio.console._

object Main extends App {

  case class CustomEnvironment(
                                override val actorContext: ActorContext[_],
                                name: String
                              ) extends BeamsEnvironment

  def customSystem(env: String, port: Int): Managed[Throwable, BeamsSupervisor.Ref[CustomEnvironment]] =
    beamsNode(
      setup = ActorSystemSetup(BootstrapSetup(
        ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port)))),
      environment = CustomEnvironment(_, env))

  def systems: Managed[Throwable, (BeamsSupervisor.Ref[CustomEnvironment], BeamsSupervisor.Ref[CustomEnvironment], BeamsSupervisor.Ref[CustomEnvironment])] = for {
    s1 <- customSystem("playground", 25520)
    s2 <- customSystem("kindergarten", 25521)
    s3 <- customSystem("garages", 25522)
  } yield (s1, s2, s3)

  def program: TaskR[Environment, Unit] = systems.use { _ =>
    for {
      _ <- putStrLn("Press any key to exit...")
      _ <- getStrLn
    } yield ()
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
