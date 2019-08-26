import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import beams.akka.cluster._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._
import scalaz.zio.console._

//TODO: Запуск кластера:
// 1. подготовка и запуск систем акторов.
// 2. ожидание некоторого состояния кластера.
// 3. запуск пользовательской программы.
object Main extends App {

  /*

    val program: TaskR[Beam[AkkaNode, String], Unit] = for {
      a <- env[String]
    } yield ()

    def main(args: Array[String]): Unit = {
      val qq = for {
        system1 <- createActorSystem(setup = setup(25520))
        system2 <- createActorSystem(setup = setup(25521))
        system3 <- createActorSystem(setup = setup(25522))
        _ <- registerRootNode("node1", system1)
        _ <- registerRootNode("node2", system2)
        _ <- registerRootNode("node3", system3)

      } yield  ()
  */

  /*
  val system1 = createClusterSystem(25520)
  val system2 = createClusterSystem(25521)
  val system3 = createClusterSystem(25522)

  Thread.sleep(3000)

  val key = serviceKey[String]

  system1.receptionist.tell(Receptionist.Subscribe(key, Behaviors.setup { ctx: ActorContext[Listing] =>
    Behaviors.receiveMessagePartial[Listing] {
      case key.Listing(l) => ???
    }
  }))

  Listing

  val b = beam(program, system1)
  val runtime = new DefaultRuntime {}
  val r = runtime.unsafeRunSync(b)
  println(r)
  system1.terminate()
  system2.terminate()
  system3.terminate()
   */

  private def setup(port: Int): ActorSystemSetup = ActorSystemSetup(BootstrapSetup(
    ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))))

  val program: TaskR[Console, Unit] = for {
    system1 <- createActorSystem(setup = setup(25520))
    system2 <- createActorSystem(setup = setup(25521))
    system3 <- createActorSystem(setup = setup(25522))
    _ <- registerRoot("node1", system1)
    _ <- registerRoot("node2", system2)
    _ <- registerRoot("node3", system3)
    _ <- getStrLn
  } yield ()

  override def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    program.foldM(error => putStrLn(error.toString) *> ZIO.succeed(1), _ => ZIO.succeed(0))
}
