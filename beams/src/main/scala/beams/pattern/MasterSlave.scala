package beams.pattern

import beams._
import zio._

object MasterSlave {

  trait Syntax[X <: Backend] {
    final def masterSlave[A](
                              masterName: String = "master",
                              slaveName: String = "slave"
                            )
                            (
                              task: Task[A] // надо ему хотя бы beams передать..
                            ): RIO[Execution[X], A] = {
      // TODO:
      // 1. Выбирается и запускается master процесс.
      // 2. Мастер публикует себя.
      // 3. Рабы обнаруживают мастера.
      // 4. Рабы публикуют себя.
      // 5. Рабы начинают ждать мастера.
      ???
    }
  }

}
