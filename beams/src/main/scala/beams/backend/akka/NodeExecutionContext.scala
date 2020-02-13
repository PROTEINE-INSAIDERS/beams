package beams.backend.akka

import scala.concurrent.ExecutionContext

private final class NodeExecutionContext(nodeActor: NodeActor.Ref[Any]) extends ExecutionContext {

  override def execute(runnable: Runnable): Unit = nodeActor ! NodeActor.Run(runnable)

  override def reportFailure(cause: Throwable): Unit = scala.concurrent.ExecutionContext.global.reportFailure(cause)
}
