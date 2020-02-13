package beams.pattern

import beams._
import zio._

object MasterSlave {

  trait Syntax[X <: Backend] {

    private object X extends Beam.Syntax[X]

    import X._

    final def masterSlave[A,
      R <: Deathwatch[X] with Discovery[X] with Exclusive[X]](
                                                               masterName: String = "master",
                                                               slaveName: String = "slave"
                                                             )
                                                             (
                                                               main: RIO[R, A]
                                                             ): RIO[R, Option[A]] =
      exclusive(masterName) {
        announce(masterName) *> main
      }.tap { r =>
        ZIO.when(r.isEmpty) {
          for {
            master <- anyNode[R]("master")
            _ <- announce(slaveName)
            _ <- deathwatch(master)
          } yield ()
        }
      }
  }

}
