/*
 * Minimal big-data–style pipeline benchmark inspired by:
 *   Gog et al. "Broom: Sweeping Out Garbage Collection from Big Data Systems"
 *   HotOS 2015, USENIX.
 *
 * The paper observes that big-data engines (Spark, Naiad, DryadLINQ) create
 * large volumes of intermediate data at each pipeline stage (map, filter,
 * group-by, reduce).  This intermediate data becomes garbage after each stage
 * or epoch and puts heavy pressure on the garbage collector.
 *
 * Broom proposes region-based (zone) memory management: allocate each
 * epoch's data in a dedicated region and free it in O(1) when the epoch ends.
 *
 * This benchmark compares four approaches on a numerical data pipeline:
 *   1. Sequential  – standard Scala collections (Vector)
 *   2. Parallel    – on-heap parallel collections (ParVector)
 *   3. Zone Seq    – off-heap sequential using Zones and manual allocation
 *   4. Zone Par    – off-heap parallel collections (ZoneParVector)
 *
 * Pipeline per epoch:  generate → map (transform) → filter → reduce
 *
 * (input for all - [dataSize]; for epochs - [dataSize] [epochs])
 */

package benchmark

import scala.collection.parallel.CollectionConverters._

import scala.scalanative.unsafe._
import scala.collection.parallel.immutable.ZoneParVector

object BroomBenchmarkSupport {

  // ── defaults ──────────────────────────────────────────────────────
//   val DefaultDataSize = 500000
  val DefaultDataSize = 10_000_000
  val DefaultEpochs   = 10
  val WarmupRuns      = 0
  val MeasuredRuns    = 5

  def banner(title: String): Unit = {
    println(s"=== $title ===")
    println(s"  warmup    : $WarmupRuns runs")
    println(s"  measured  : $MeasuredRuns runs")
    println()
  }

  def rawInput(dataSize: Int): Array[Long] =
    Array.tabulate(dataSize)(i => i.toLong + 1L)

  def dataSizesFromArgs(args: Array[String]): Seq[Int] =
    if (args.length > 0) Seq(args(0).toInt)
    // else (500 to 3000 by 500)
    else (1_000_000 to 6_000_000 by 1_000_000)

  // ═══════════════════════════════════════════════════════════════════
  //  1.  Single-pass pipeline:  map → filter → reduce
  // ═══════════════════════════════════════════════════════════════════

  def pipelineSequential(input: Array[Long]): Long = {
    val data = Vector.tabulate(input.length)(i => input(i))
    data
      .map(x => x * 3 + 17)
      .filter(x => x % 7 != 0)
      .foldLeft(0L)(_ + _)
  }

  def pipelineParallel(input: Array[Long]): Long = {
    val data = Vector.tabulate(input.length)(i => input(i)).par
    data
      .map(x => x * 3 + 17)
      .filter(x => x % 7 != 0)
      .fold(0L)(_ + _)
  }

  def pipelineZoneSequential(input: Array[Long]): Long = {
    Zone.acquire { implicit zone =>
      val len = input.length
      val data = alloc[Long](len)
      var k = 0
      while (k < len) {
        data(k) = input(k)
        k += 1
      }

      var i = 0
      var sum = 0L
      while (i < len) {
        val v = data(i) * 3 + 17
        if (v % 7 != 0) sum += v
        i += 1
      }
      sum
    }
  }

  def pipelineZonePar(input: Array[Long]): Long = {
    Zone.acquire { implicit zone =>
      val data = ZoneParVector.tabulate(input.length)(i => input(i))
      data
        .map(x => x * 3 + 17)
        .filter(x => x % 7 != 0)
        .fold(0L)(_ + _)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  2.  Multi-epoch pipeline  (Broom's primary scenario)
  //      Each epoch: build collection → transform → aggregate → free
  // ═══════════════════════════════════════════════════════════════════

  def epochsSequential(input: Array[Long], epochs: Int): Long = {
    var total = 0L
    var epoch = 0
    while (epoch < epochs) {
      val data = Vector.tabulate(input.length)(i => input(i) + epoch)
      total += data
        .map(x => x * 3 + 17)
        .filter(x => x % 7 != 0)
        .foldLeft(0L)(_ + _)
      epoch += 1
      // intermediates become garbage – GC must reclaim
    }
    total
  }

  def epochsParallel(input: Array[Long], epochs: Int): Long = {
    var total = 0L
    var epoch = 0
    while (epoch < epochs) {
      val data = Vector.tabulate(input.length)(i => input(i) + epoch).par
      total += data
        .map(x => x * 3 + 17)
        .filter(x => x % 7 != 0)
        .fold(0L)(_ + _)
      epoch += 1
    }
    total
  }

  def epochsZoneSequential(input: Array[Long], epochs: Int): Long = {
    var total = 0L
    var epoch = 0
    while (epoch < epochs) {
      Zone.acquire { implicit zone =>
        val len = input.length
        val data = alloc[Long](len)
        var k = 0
        while (k < len) {
          data(k) = input(k) + epoch
          k += 1
        }

        var i = 0
        var sum = 0L
        while (i < len) {
          val v = data(i) * 3 + 17
          if (v % 7 != 0) sum += v
          i += 1
        }
        total += sum
      }
      epoch += 1
    }
    total
  }

  def epochsZonePar(input: Array[Long], epochs: Int): Long = {
    var total = 0L
    var epoch = 0
    while (epoch < epochs) {
      Zone.acquire { implicit zone =>
        val data = ZoneParVector.tabulate(input.length)(i => input(i) + epoch)
        total += data
          .map(x => x * 3 + 17)
          .filter(x => x % 7 != 0)
          .fold(0L)(_ + _)
      } // zone freed in O(1) – no GC pause for this epoch's data
      epoch += 1
    }
    total
  }

  // ═══════════════════════════════════════════════════════════════════
  //  3.  Aggregation pipeline:  group-by bucket → sum per bucket
  //      Models SELECT bucket, SUM(value) FROM data GROUP BY bucket
  // ═══════════════════════════════════════════════════════════════════

  private val NumBuckets = 256

  def aggregateSequential(input: Array[Long]): Long = {
    val data = Vector.tabulate(input.length)(i => input(i))
    val grouped = data.groupBy(x => (x % NumBuckets).toInt)
    grouped.values.map(_.foldLeft(0L)(_ + _)).foldLeft(0L)(_ + _)
  }

  def aggregateParallel(input: Array[Long]): Long = {
    val data = Vector.tabulate(input.length)(i => input(i)).par
    val grouped = data.groupBy(x => (x % NumBuckets).toInt)
    grouped.map(_._2.fold(0L)(_ + _)).fold(0L)(_ + _)
  }

  def aggregateZoneSequential(input: Array[Long]): Long = {
    Zone.acquire { implicit zone =>
      val len = input.length
      val data = alloc[Long](len)
      var k = 0
      while (k < len) {
        data(k) = input(k)
        k += 1
      }

      val buckets = alloc[Long](NumBuckets)
      var bidx = 0
      while (bidx < NumBuckets) {
        buckets(bidx) = 0L
        bidx += 1
      }

      var i = 0
      while (i < len) {
        val x = data(i)
        val b = (x % NumBuckets).toInt
        buckets(b) = buckets(b) + x
        i += 1
      }

      var sum = 0L
      var j = 0
      while (j < NumBuckets) {
        sum += buckets(j)
        j += 1
      }
      sum
    }
  }

  def aggregateZonePar(input: Array[Long]): Long = {
    Zone.acquire { implicit zone =>
      val data = ZoneParVector.tabulate(input.length)(i => input(i))
      val grouped = data.groupBy(x => (x % NumBuckets).toInt)
      grouped.map(_._2.fold(0L)(_ + _)).fold(0L)(_ + _)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  Timing harness
  // ═══════════════════════════════════════════════════════════════════

  def runBenchmark(label: String, warmup: Int, measured: Int)
                          (body: => Long): Unit = {
    // warmup
    var w = 0
    while (w < warmup) { body; w += 1 }

    // measure
    val times = new Array[Long](measured)
    var checksum = 0L
    var i = 0
    while (i < measured) {
      val t0 = System.nanoTime()
      checksum = body
      val t1 = System.nanoTime()
      times(i) = t1 - t0
      i += 1
    }

    // print individual run times, one per row
    println("    runs:")
    times.zipWithIndex.foreach { case (t, idx) =>
      println(f"      ${idx + 1}%2d:${ms(t)}")
    }

    val sorted = times.sorted
    val median = sorted(sorted.length / 2)
    val mean   = times.sum / times.length
    val min    = sorted.head
    val max    = sorted.last

    // println(f"  $label%-14s  median=${ms(median)}%8s  mean=${ms(mean)}%8s  " +
    //         f"min=${ms(min)}%8s  max=${ms(max)}%8s  (checksum=$checksum)")
  }

  private def ms(nanos: Long): String = f"${nanos / 1e6}%.1f ms"
}

object SinglePassSequentialBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Single-Pass Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Sequential", WarmupRuns, MeasuredRuns)(pipelineSequential(input))
    }
  }
}

object SinglePassParallelBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Single-Pass Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Parallel", WarmupRuns, MeasuredRuns)(pipelineParallel(input))
    }
  }
}

object SinglePassZoneSequentialBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Single-Pass Pipeline Benchmark (Zone Sequential)")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Zone Seq", WarmupRuns, MeasuredRuns)(pipelineZoneSequential(input))
    }
  }
}

object SinglePassZoneParBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Single-Pass Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Zone Par", WarmupRuns, MeasuredRuns)(pipelineZonePar(input))
    }
  }
}

object SinglePassCombinedBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Single-Pass Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Sequential", WarmupRuns, MeasuredRuns)(pipelineSequential(input))
      runBenchmark("Parallel", WarmupRuns, MeasuredRuns)(pipelineParallel(input))
      runBenchmark("Zone Seq", WarmupRuns, MeasuredRuns)(pipelineZoneSequential(input))
      runBenchmark("Zone Par", WarmupRuns, MeasuredRuns)(pipelineZonePar(input))
    }
  }
}

object MultiEpochSequentialBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    val epochs   = if (args.length > 1) args(1).toInt else DefaultEpochs
    for (dataSize <- sizes) {
      banner("Broom-Inspired Multi-Epoch Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println(s"  epochs    : $epochs")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Sequential", WarmupRuns, MeasuredRuns)(epochsSequential(input, epochs))
    }
  }
}

object MultiEpochParallelBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    val epochs   = if (args.length > 1) args(1).toInt else DefaultEpochs
    for (dataSize <- sizes) {
      banner("Broom-Inspired Multi-Epoch Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println(s"  epochs    : $epochs")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Parallel",   WarmupRuns, MeasuredRuns)(epochsParallel(input, epochs))
    }
  }
}

object MultiEpochZoneSequentialBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    val epochs   = if (args.length > 1) args(1).toInt else DefaultEpochs
    for (dataSize <- sizes) {
      banner("Broom-Inspired Multi-Epoch Pipeline Benchmark (Zone Sequential)")
      println(s"  data size : $dataSize elements")
      println(s"  epochs    : $epochs")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Zone Seq", WarmupRuns, MeasuredRuns)(epochsZoneSequential(input, epochs))
    }
  }
}

object MultiEpochZoneParBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    val epochs   = if (args.length > 1) args(1).toInt else DefaultEpochs
    for (dataSize <- sizes) {
      banner("Broom-Inspired Multi-Epoch Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println(s"  epochs    : $epochs")
      println()

      val input = rawInput(dataSize)

      // This is the core Broom scenario: each epoch creates temporaries
      // that become garbage; zones free them in O(1) per epoch.
      runBenchmark("Zone Par",   WarmupRuns, MeasuredRuns)(epochsZonePar(input, epochs))
    }
  }
}

object AggregationSequentialBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Aggregation Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Sequential", WarmupRuns, MeasuredRuns)(aggregateSequential(input))
    }
  }
}

object AggregationParallelBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Aggregation Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Parallel", WarmupRuns, MeasuredRuns)(aggregateParallel(input))
    }
  }
}

object AggregationZoneSequentialBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Aggregation Pipeline Benchmark (Zone Sequential)")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Zone Seq", WarmupRuns, MeasuredRuns)(aggregateZoneSequential(input))
    }
  }
}

object AggregationZoneParBroomBenchmark {
  import BroomBenchmarkSupport._

  def main(args: Array[String]): Unit = {
    val sizes = dataSizesFromArgs(args)
    for (dataSize <- sizes) {
      banner("Broom-Inspired Aggregation Pipeline Benchmark")
      println(s"  data size : $dataSize elements")
      println()

      val input = rawInput(dataSize)

      runBenchmark("Zone Par", WarmupRuns, MeasuredRuns)(aggregateZonePar(input))
    }
  }
}
