import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed._
import beams.akka.AkkaBeam
import beams.akka.cluster._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import scalaz.zio._

object Main {

  val program: TaskR[AkkaBeam[String], Unit] = for {
    a <- env[String]
  } yield ()

  def main(args: Array[String]): Unit = {
    startClusterNode(25520)
    startClusterNode(25521)
    val system = startClusterNode(25522)
  }

  private def startClusterNode(port: Int): ActorSystem[Nothing] = { // 25520
    val config = ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))
    createActorSystem(env = (), setup = ActorSystemSetup(BootstrapSetup(config)))
  }
}
