import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

object CopilotWebBench {
  // 1. Regular GC-managed class
  class PointGC(var x: Int, var y: Int)

  // 2. Zone-managed CStruct
  type PointZone = CStruct2[Int, Int]

  // Helper method for GC allocations
  // Returns a checksum of fields so allocations are observed by optimizer
  @noinline def runGC(count: Int): Long = {
    var i = 0
    var sum: Long = 0L
    while (i < count) {
      val p = new PointGC(i, i)
      sum += p.x.toLong + p.y.toLong
      i += 1
    }
    sum
  }

  // Helper method for Zone allocations
  // Returns a checksum of fields so allocations are observed by optimizer
  @noinline def runZone(count: Int): Long = {
    var sum: Long = 0L
    Zone.acquire { implicit z =>
      var i = 0
      while (i < count) {
        // alloc[] allocates memory inside the current implicit Zone
        val p = alloc[PointZone]()
        p._1 = i
        p._2 = i
        sum += p._1.toLong + p._2.toLong
        i += 1
      }
    } // Entire zone is efficiently freed in O(1) time here
    sum
  }
}

// Small wrapper to run only the GC allocation benchmark
object CopilotWebBenchGC {
  def main(args: Array[String]): Unit = {
    val allocations = 100_000_000
    println(s"Running GC-only benchmark: allocating $allocations objects...")
    val start = System.nanoTime()
    val checksum = CopilotWebBench.runGC(allocations)
    val end = System.nanoTime()
    val timeMs = (end - start) / 1_000_000.0
    println(f"GC Allocation Time: $timeMs%8.2f ms")
    println(s"Checksum: $checksum")
  }
}

// Small wrapper to run only the Zone allocation benchmark
object CopilotWebBenchZone {
  def main(args: Array[String]): Unit = {
    val allocations = 100_000_000
    println(s"Running Zone-only benchmark: allocating $allocations objects...")
    val start = System.nanoTime()
    val checksum = CopilotWebBench.runZone(allocations)
    val end = System.nanoTime()
    val timeMs = (end - start) / 1_000_000.0
    println(f"Zone Allocation Time: $timeMs%8.2f ms")
    println(s"Checksum: $checksum")
  }
}