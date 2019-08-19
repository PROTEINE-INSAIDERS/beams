import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.ClusterEvent._
import akka.cluster.{Member, MemberStatus}
import akka.cluster.typed._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import beams.akka._
import scalaz.zio._
import akka.actor.{Address, BootstrapSetup}
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Main {

  def main(args: Array[String]): Unit = {
    startClusterNode(25520)
    startClusterNode(25521)
    val system = startClusterNode(25522)

    implicit val ct: Timeout = Timeout(10 seconds)
    implicit val sch: Scheduler = system.scheduler


    // val ff: Future[Listing] =

      Thread.sleep(5000)

    val r = Await.result(system.receptionist.ask[Listing](replyTo => Receptionist.Find(key, replyTo))(ct, sch) , Duration.Inf)
    println(r)
    val instances = r.serviceInstances(key)
    instances.foreach(println)
    // system.receptionist.ask(replyTo => Receptionist.Find(???, )) Receptionist.Find


    //    val listenerRef = untypedSystem.spawn(clusterListener, "clusterListener")
    //    cluster.manager ! Join(cluster.selfMember.address)
    //    cluster.subscriptions ! Subscribe(listenerRef, classOf[MemberEvent])
    //    scala.io.StdIn.readLine()
    ()
  }

  private def clusterListener: Behavior[MemberEvent] = Behaviors.setup[MemberEvent] { ctx =>
    Behaviors.receiveMessagePartial {
      case MemberJoined(member) =>
        println(s"MemberJoined($member)")
        Behaviors.same
      case MemberUp(member) =>
        println(s"MemberUp($member)")
        Behaviors.same
    }
  }

  val key = ServiceKey[NodeActor.Command]("beam-node")

  private def startClusterNode(port: Int) : ActorSystem[Nothing] = { // 25520
    val config = ConfigFactory.defaultApplication().withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))
    beams.akka.cluster.createActorSystem(setup = ActorSystemSetup(BootstrapSetup(config)))
  }
}
