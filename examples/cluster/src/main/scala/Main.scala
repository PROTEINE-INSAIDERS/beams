import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.cluster.typed._
import akka.actor.typed.scaladsl.adapter._


object Main {
  def main(args: Array[String]): Unit = {
    val untypedSystem = akka.actor.ActorSystem("ClusterSystem")
    val system = untypedSystem.toTyped
    val cluster = Cluster(system)
    scala.io.StdIn.readLine()
    ()
  }
}
