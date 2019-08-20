import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import beams.Beam
import beams.akka.AkkaNode
import beams.akka.cluster._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._

object Main {

  val program: TaskR[Beam[AkkaNode, String], Unit] = for {
    a <- env[String]
  } yield ()

  def main(args: Array[String]): Unit = {
    val system1 = startClusterNode(25520)
    val system2 = startClusterNode(25521)
    val system3 = startClusterNode(25522)
    Thread.sleep(2000)

    val b = beam[String, Unit](program, system1)
    val runtime = new DefaultRuntime {}
    val r = runtime.unsafeRunSync(b)
    println(r)
    system1.terminate()
    system2.terminate()
    system3.terminate()
  }

  private def startClusterNode(port: Int): ActorSystem[Nothing] = { // 25520
    val config = ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))
    createActorSystem(env = "", setup = ActorSystemSetup(BootstrapSetup(config)))
  }
}
