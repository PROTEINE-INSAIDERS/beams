import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus
import akka.cluster.typed._

object Main {
  def main(args: Array[String]): Unit = {

    val system1 = beams.akka.local.createActorSystem("system1")

    val cluster1 = Cluster(system1)

    cluster1.manager ! Join(cluster1.selfMember.address)

    // примерная схема работы кластера:
    // 1. поднимается система с NodeActor.
    // 2. поднимается менеджер, следящий за кластером.
    // 3. менеджер содержит список нод, которые могут быть использованы для запуска программ.   

    cluster1.manager ! Leave(cluster1.selfMember.address)
  }
}
