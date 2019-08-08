package beams
/*
import cats._
import cats.data._
import cats.implicits._
import cats.mtl._

package object mtl {
  implicit def applicativeAskForContT[F[_] : Defer : FlatMap, R, E](
                                                                     implicit F: ApplicativeAsk[F, E]
                                                                   ): ApplicativeAsk[ContT[F, R, ?], E] =
    new ApplicativeAsk[ContT[F, R, ?], E] {
      override val applicative: Applicative[ContT[F, R, ?]] = ContT.catsDataContTMonad[F, R]

      override def ask: ContT[F, R, E] = ContT(cont => ApplicativeAsk.ask[F, E].flatMap(cont))

      override def reader[A](f: E => A): ContT[F, R, A] = ask.map(f)
    }
}
*/