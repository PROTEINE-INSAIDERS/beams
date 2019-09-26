import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed.scaladsl._
import beams.akka._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._
import scalaz.zio.console._

import scala.reflect.runtime.universe

object Main extends App {

  class Playground(ctx: ActorContext[Node.Command[Playground]]) extends AkkaBeam with Console.Live {
    override val nodeActor: Node.Ref[_] = ctx.self
  }

  class Kindergarten(ctx: ActorContext[Node.Command[Kindergarten]]) extends AkkaBeam with Console.Live {
    override val nodeActor: Node.Ref[_] = ctx.self
  }

  class Garages(ctx: ActorContext[Node.Command[Garages]]) extends AkkaBeam with Console.Live {
    override val nodeActor: Node.Ref[_] = ctx.self
  }

  def myNode[R: universe.TypeTag](f: ActorContext[Node.Command[R]] => R, port: Int): Managed[Throwable, Node.Ref[R]] =
    node(
      setup = ActorSystemSetup(BootstrapSetup(
        ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port)))),
      environment = { ctx: ActorContext[Node.Command[R]] => f(ctx) })

  def myNodes: Managed[Throwable, (Node.Ref[Playground], Node.Ref[Kindergarten], Node.Ref[Garages])] = for {
    // scalastyle:off magic.number
    s1 <- myNode(new Playground(_), 25520)
    s2 <- myNode(new Kindergarten(_), 25521)
    s3 <- myNode(new Garages(_), 25522)
    // scalastyle:on magic.number
  } yield (s1, s2, s3)

  def printEnv[R <: Console]: ZIO[R, Nothing, Unit] = ZIO.environment[R].flatMap { e => putStrLn(s"running at $e") }

  def beam: TaskR[AkkaBeam with Console, Unit] = for {
    _ <- putStrLn("hello from beam")
    playground <- someNodes[Playground].map(_.head)
    kindergarten <- someNodes[Kindergarten].map(_.head)
    garages <- someNodes[Garages].map(_.head)
    _ <- runAt(playground)(printEnv)
    _ <- runAt(kindergarten)(printEnv)
    _ <- runAt(garages)(printEnv)
  } yield ()

  def program: TaskR[Environment, Unit] = myNodes.use { case (n1, _, _) =>
    for {
      _ <- submitTo(n1)(beam)
      _ <- putStrLn("Press any key to exit...")
      _ <- getStrLn
      _ <- putStrLn("exiting...")
    } yield ()
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
