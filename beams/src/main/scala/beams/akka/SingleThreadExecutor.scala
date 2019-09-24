package beams.akka

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}

import scalaz.zio.internal.{ExecutionMetrics, Executor}

/**
  * Execute tasks on a single thread.
  */
private trait SingleThreadExecutor extends Executor {
  private var thread: Thread = _

  private var stopped = false

  private val submissionProhibited: AtomicBoolean = new AtomicBoolean(false)

  private val taskQueue: BlockingQueue[Runnable] = new LinkedBlockingQueue[Runnable]()

  def enterLoop(): Unit = {
    thread = Thread.currentThread
    try {
      while (!stopped) {
        val task = taskQueue.take()
        task.run()
      }
    } finally {
      thread = null
    }
  }

  //TODO: add metrics
  override def metrics: Option[ExecutionMetrics] = None

  override def submit(runnable: Runnable): Boolean = {
    if (submissionProhibited.get) {
      throw new Exception("Submission prohibited: SingleThreadExecutor received stop request.")
    }
    taskQueue.add(runnable)
  }

  override def here: Boolean = {
    Thread.currentThread == thread
  }

  def stop(): Unit = {
    if (submissionProhibited.compareAndSet(false, true)) {
      taskQueue.put(new Runnable {
        override def run(): Unit = SingleThreadExecutor.this.stopped = true
      })
    }
  }
}

private object SingleThreadExecutor {
  def apply(
             // scalastyle:off magic.number
             yieldOpCount0: Int = 2048
             // scalastyle:on magic.number
           ): SingleThreadExecutor = new SingleThreadExecutor {
    override def yieldOpCount: Int = yieldOpCount0
  }
}
