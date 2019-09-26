import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import beams.akka._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._
import scalaz.zio.console._

object Main extends App {

  abstract class NodeEnv(ctx: NodeActor.Ctx[_]) extends AkkaBeam with Console.Live {
    override val nodeActor = ctx.self
  }

  final class AliceEnv(ctx: NodeActor.Ctx[_]) extends NodeEnv(ctx)

  final class BobEnv(ctx: NodeActor.Ctx[_]) extends NodeEnv(ctx)

  final class WalterEnv(ctx: NodeActor.Ctx[_]) extends NodeEnv(ctx)

  def actorSystem(port: Int) = beams.akka.actorSystem(setup = ActorSystemSetup(BootstrapSetup(
    ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port)))))

  val aliceNode = actorSystem(25520).flatMap(node(new AliceEnv(_)).provide)
  val bobNode = actorSystem(25521).flatMap(node(new BobEnv(_)).provide)
  val walterNode = actorSystem(25521).flatMap(node(new WalterEnv(_)).provide)

  def factorial(n: Int): Int = n match {
    case 0 => 1
    case _ => n * factorial(n - 1)
  }

  val beam = for {
    alice <- anyNode[AliceEnv]
    bob <- anyNode[BobEnv]

    x <- ZIO.effectTotal(factorial(6))
     
  } yield {
    ()
  }

  def program: TaskR[Environment, Unit] = (aliceNode *> bobNode *> walterNode) use { walter =>
    for {
      _ <- submitTo(walter)(beam)
      _ <- putStrLn("Press any key to exit...")
      _ <- getStrLn
      _ <- putStrLn("exiting...")
    } yield ()
  }

  override def run(args: List[String]): ZIO[Environment, Nothing, Int]
  =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
