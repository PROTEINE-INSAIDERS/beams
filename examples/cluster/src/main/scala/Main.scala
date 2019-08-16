import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.cluster.typed._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams.akka._
import scalaz.zio._
import akka.actor.Address
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}


object Main {

  private def clusterListener = Behaviors.setup[MemberEvent] { ctx =>
    Behaviors.receiveMessagePartial {
      case MemberJoined(member) =>
        println(s"MemberJoined($member)")
        Behaviors.same
      case MemberUp(member) =>
        println(s"MemberUp($member)")
        Behaviors.same
    }
  }

  // val probe1 = TestProbe[MemberEvent]()(system1)
  // cluster1.subscriptions ! Subscribe(probe1.ref, classOf[MemberEvent])

  private def startClusterNode(port: Int): Unit = { // 25520
    val config = ConfigFactory.defaultApplication()
    val untypedSystem = akka.actor.ActorSystem("ClusterSystem", config.withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port)))
    val system = untypedSystem.toTyped
    val cluster = Cluster(system)

    val beamNode = untypedSystem.spawn(NodeActor(()), "beam-node")
    val key = ServiceKey("beam-node")


  }

  def main(args: Array[String]): Unit = {
    startClusterNode(25520)
    startClusterNode(25521)
    startClusterNode(25522)



//    val listenerRef = untypedSystem.spawn(clusterListener, "clusterListener")
//    cluster.manager ! Join(cluster.selfMember.address)
//    cluster.subscriptions ! Subscribe(listenerRef, classOf[MemberEvent])
//    scala.io.StdIn.readLine()
    ()
  }
}
