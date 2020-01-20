import akka.actor.BootstrapSetup
import akka.actor.setup._
import beams.akka._
import beams.{Beam, Engine}
import com.typesafe.config._
import zio._
import zio.console._

/**
 * This is an adaptation of introductory distributed program taken from Unison programming language (https://github.com/unisonweb/unison)
 */
object Main extends App {

  /**
   * Node is the basic concept of Вeams framework. Nodes are capable to run Вeams tasks and provide local environment
   * which is accessible by task running on current node. This environment provides Вeams services itself by extending
   * [[beams.akka.AkkaBeam]] as well as ZIO's console to print text messages.
   */
  abstract class NodeEnv[X <: Engine] extends Beam[X] with Console.Live

  /**
   * Each node can have it's onw environment type to provide node specific services. In this example we will create two
   * different type of environments for Alice and Bob. This fact however will not be used any further. Alice's and Bob's
   * environments are semantically identical and created for demonstration purposes only.
   */
  final class AliceEnv[X <: Engine](override val beam: Beam.Service[Any, X]) extends NodeEnv[X]

  final class BobEnv[X <: Engine](override val beam: Beam.Service[Any, X]) extends NodeEnv[X]

  /**
   * Beams implied for distributed programming and you likely will want to run different Вeams nodes on different computers.
   * For the sake of simplicity this exampe will use two different actor system running in a same program to simulate
   * distributed system.
   */
  private def actorSystem(port: Int) = beams.akka.root(setup = ActorSystemSetup(BootstrapSetup(
    ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port)))))

  private val aliceNode = actorSystem(25520).flatMap(node(beam => new AliceEnv(beam.beam)).provide)
  private val bobNode = actorSystem(25521).flatMap(node(beam => new BobEnv(beam.beam)).provide)

  private def factorial(n: Int): Int = n match {
    case 0 => 1
    case _ => n * factorial(n - 1)
  }

  private def foo[X <: Engine](x: Int) = for {
    env <- ZIO.environment[NodeEnv[X]]
    _ <- putStrLn(s"running foo at $env")
  } yield x + 1

  private def bar[X <: Engine](x: Int, y: Int) = for {
    env <- ZIO.environment[NodeEnv[X]]
    _ <- putStrLn(s"running bar at $env")
  } yield (x + y)

  def program: RIO[ZEnv, Unit] = (aliceNode <*> bobNode) use {
    /**
     * In this example direct references to Alice and Bob nodes available. In real distributed application such
     * references should be discovered with [[beams.BeamsSyntax.nodeListing]] or [[beams.BeamsSyntax.someNodes]]
     * and [[beams.BeamsSyntax.anyNode]] helper functions.
     */
    case (alice, bob) =>

      /**
       * Since Scala does not support val bindings as first statement in for expressions factorial calculated outside
       * of for statement.
       */
      val x = factorial(6)
      for {
        /**
         * Unlike Unison, Beams does not support stong code mobility. Hence it's not possibe to 'transfer' execution of
         * current program to the remote node. But it possible to create closure which captures current execution state
         * and submit it to remote node.
         *
         * You should be careful not to capture too much state or something which can not be serialized. Same rule
         * applies to any distributed framework relying on closure serialization, such as Spark, for example.
         *
         * Please note, that [[beams.BeamsSyntax.submitTo]] will run remote process in fire-and-forget fashion.
         * You will not be able to interrupt remote task nor obtain it's result. Use [[beams.BeamsSyntax.runAt]] for
         * guided launch.
         */
        _ <- submitTo(alice) {
          for {
            y <- foo[AkkaEngine](x)
            _ <- submitTo(bob) {
              for {
                res <- bar[AkkaEngine](x, y)
                _ <- putStrLn(s"The result is $res")
              } yield ()
            }
          } yield ()
        }

        /**
         * Since we do not control submitted tasks and not having idea when them will terminate, main program should be
         * terminated manually.
         */
        _ <- putStrLn("Press any key to exit...")
        _ <- getStrLn
      } yield ()
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
