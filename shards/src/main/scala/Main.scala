import xerial.larray._
import xerial.larray.mmap._



object Main {
  def main(args: Array[String]): Unit = {
    //val a = new MMapBuffer()
    println("start")
    var mem = UnsafeUtil.unsafe.allocateMemory(2147483648L)
    println(mem )
    mem = UnsafeUtil.unsafe.reallocateMemory(mem, 4294967296L)
    println(mem )
    UnsafeUtil.unsafe.freeMemory(mem)
    println("done")
  }
}
