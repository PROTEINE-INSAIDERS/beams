package beams

import _root_.akka.actor._
import beams.mtl._
import cats._
import cats.arrow.FunctionK
import cats.data._
import cats.effect.concurrent.Deferred
import cats.effect.{ContextShift, IO, LiftIO}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import _root_.akka.actor.typed.scaladsl.adapter._

package object akka {
  type AkkaT[F[_], A] = ContT[ReaderT[F, LocalState, ?], Unit, A]

  implicit def clusterForAkka[F[_] : Defer : FlatMap](
                                                       implicit F: ApplicativeAsk[ReaderT[F, LocalState, ?], LocalState]
                                                     ): Cluster[AkkaT[F, ?], ActorRef] = new Cluster[AkkaT[F, ?], ActorRef] {
    override def nodes: AkkaT[F, NonEmptyList[ActorRef]] = ApplicativeAsk.readerFE[AkkaT[F, ?], LocalState] { n =>
      println(s"==== Nodes ${n.nodes}")
      n.nodes
    }
  }

  implicit def beamForAkkaLocal[F[_] : Applicative]: Beam[AkkaT[F, ?], ActorRef] = new Beam[AkkaT[F, ?], ActorRef] {
    override def beamTo(node: ActorRef): AkkaT[F, Unit] = ContT { cont =>
      println(s"==== beam to $node")
      ().pure[ReaderT[F, LocalState, ?]]
    }
  }

  //def run[F[_], A](program: AkkaT[F, A], compiler: FunctionK[F, IO], executors: NonEmptyList[ActorRef]) = {
   // ???
  //}

  def run[F[_] : LiftIO : Monad, A](
                                     process: AkkaT[F, A],
                                     nodesCount: Int,
                                     compiler: FunctionK[F, IO]
                                   )
                                   (
                                     implicit actorSystem: ActorSystem,
                                     contextShift: ContextShift[IO]
                                   ): F[A] = LiftIO[F].liftIO(for {
    dresult <- Deferred[IO, A]
    actors <- IO {
      val a = actorSystem.toTyped



      NonEmptyList.fromListUnsafe(List.fill(nodesCount)(actorSystem.actorOf(NodeActor.props(compiler))))
    }
    _ <- actors.traverse_ { actor =>
      IO {
        actor ! DiscoverMessage(actors)
      }
    }
    _ <- IO {
      actors.head ! BeamMessage[F](process.run { aaa =>
        ReaderT { env =>
          LiftIO[F].liftIO(IO {
            println("=== в рот")
            ()
          } *> dresult.complete(aaa))
        }
      })
    }
    result <- dresult.get
  } yield result)
}
