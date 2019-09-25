import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed.scaladsl._
import beams.akka._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._
import scalaz.zio.console._

object Main extends App {

  case class MyEnvironment(
                            override val nodeActor: BeamsSupervisor.Ref[_],
                            name: String
                          ) extends AkkaBeam with Console.Live

  def myNode(env: String, port: Int): Managed[Throwable, BeamsSupervisor.Ref[MyEnvironment]] =
    node(
      setup = ActorSystemSetup(BootstrapSetup(
        ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port)))),
      environment = { ctx: ActorContext[BeamsSupervisor.Command[MyEnvironment]] => MyEnvironment(ctx.self, env) })

  def myNodes: Managed[Throwable, (BeamsSupervisor.Ref[MyEnvironment], BeamsSupervisor.Ref[MyEnvironment], BeamsSupervisor.Ref[MyEnvironment])] = for {
    s1 <- myNode("playground", 25520)
    s2 <- myNode("kindergarten", 25521)
    s3 <- myNode("garages", 25522)
  } yield (s1, s2, s3)

  def beam: TaskR[MyEnvironment, Unit] = for {
    _ <- putStrLn("hello from beam")
    nodes <- listing[MyEnvironment].use { queue: Queue[Set[BeamsSupervisor.Ref[MyEnvironment]]] =>
      def next: ZIO[Any, Throwable, Set[BeamsSupervisor.Ref[MyEnvironment]]] = queue.take.flatMap(set =>
        if (set.size < 3)
          next
        else
          ZIO.succeed(set))

      next
    }
    _ <- putStrLn(s"all nodes up: $nodes")
    // name <- ZIO.access[MyEnvironment](_.name)
    // _ <- putStrLn(s"running at $name")
    fibers <- ZIO.foreach(nodes) { node =>
      forkTo(node) {
        for {
          name <- ZIO.access[MyEnvironment](_.name)
          _ <- putStrLn(s"running at $name")
        } yield ()
      }
    }
    _ <- Fiber.joinAll(fibers)
  } yield ()

  def program: TaskR[Environment, Unit] = myNodes.use { case (n1, _, _) =>
    for {
      _ <- submit(n1, beam)
      _ <- putStrLn("Press any key to exit...")
      _ <- getStrLn
      _ <- putStrLn("exiting...")
    } yield ()
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
