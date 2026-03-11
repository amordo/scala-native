import scala.language.experimental.captureChecking

import scala.scalanative.memory.SafeZone
import scala.scalanative.runtime.SafeZoneAllocator.allocate

object SafeZoneBenchmarksRefactored {
  final case class RunResult(totalNs: Long, checksum: Long)
  final case class SweepResult(n: Int, totalObjects: Long, meanTotalNs: Double, checksum: Long)

  private val DefaultIterations = 100_000_000_000L
  private val DefaultWarmupRounds = 0 //3
  private val DefaultMeasuredRounds = 1 //5
  private val Separator = "=" * 60
  @volatile private var sink = 0L

  private def propertyInt(name: String, defaultValue: Int): Int = {
    Option(System.getProperty(name))
      .flatMap(value => value.toIntOption)
      .filter(_ > 0)
      .getOrElse(defaultValue)
  }

  private def propertyLong(name: String, defaultValue: Long): Long = {
    Option(System.getProperty(name))
      .flatMap(value => value.toLongOption)
      .filter(_ > 0L)
      .getOrElse(defaultValue)
  }

  private def formatNs(ns: Double): String = {
    if (ns >= 1_000_000.0) f"${ns / 1_000_000.0}%.3f ms"
    else if (ns >= 1_000.0) f"${ns / 1_000.0}%.3f us"
    else f"$ns%.2f ns"
  }

  private def propertyBoundedInt(name: String, defaultValue: Int, minValue: Int): Int = {
    Option(System.getProperty(name))
      .flatMap(value => value.toIntOption)
      .filter(_ >= minValue)
      .getOrElse(defaultValue)
  }

  def runBenchmark(name: String)(loop: Long => Long): Unit = {
    val iterations = propertyLong("safezone.benchmark.iterations", DefaultIterations)
    val warmupRounds = propertyInt("safezone.benchmark.warmups", DefaultWarmupRounds)
    val measuredRounds = propertyInt("safezone.benchmark.runs", DefaultMeasuredRounds)

    println(Separator)
    println(s"Running $name")
    println(Separator)
    println(s"Iterations: $iterations")
    println(s"Warmup rounds: $warmupRounds")
    println(s"Measured rounds: $measuredRounds")
    println()

    def measureRound(): RunResult = {
      val startTime = System.nanoTime()
      val checksum = loop(iterations)
      val totalNs = System.nanoTime() - startTime

      sink = checksum
      RunResult(totalNs, checksum)
    }

    var warmup = 0
    while (warmup < warmupRounds) {
      measureRound()
      warmup += 1
    }

    val samples = new Array[RunResult](measuredRounds)
    var round = 0
    while (round < measuredRounds) {
      samples(round) = measureRound()
      round += 1
    }

    val checksums = samples.map(_.checksum)
    val expectedChecksum = checksums.head
    if (!checksums.forall(_ == expectedChecksum)) {
      val observed = checksums.mkString(", ")
      throw new IllegalStateException(s"Inconsistent benchmark checksum across runs: $observed")
    }

    val totals = samples.map(_.totalNs)
    val sortedTotals = totals.sorted
    val minNs = sortedTotals.head
    val maxNs = sortedTotals.last
    val medianNs = sortedTotals(sortedTotals.length / 2)
    val meanNs = totals.sum.toDouble / totals.length.toDouble
    val avgPerIterationNs = meanNs / iterations.toDouble

    println()
    println(Separator)
    println("Timing Summary:")
    println(Separator)
    samples.zipWithIndex.foreach { case (sample, index) =>
      println(s"Measure ${index + 1}: ${formatNs(sample.totalNs.toDouble)} total")
    }
    println(s"Per iteration (mean): ${formatNs(avgPerIterationNs)}")
    println(s"Total per run (min): ${formatNs(minNs.toDouble)}")
    println(s"Total per run (median): ${formatNs(medianNs.toDouble)}")
    println(s"Total per run (max): ${formatNs(maxNs.toDouble)}")
    println(s"Total per run (mean): ${formatNs(meanNs)}")
    println(s"Checksum: $expectedChecksum")
    println(s"Sink: $sink")
    println(Separator)
  }

  def runListOfListsSweepBenchmark(name: String)(runForN: Int => Long): Unit = {
    val minN = propertyBoundedInt("safezone.listbench.minN", 500, 1)
    val maxN = propertyBoundedInt("safezone.listbench.maxN", 3000, minN)
    val stepN = propertyBoundedInt("safezone.listbench.stepN", 500, 1)
    val warmupRounds = propertyInt("safezone.benchmark.warmups", DefaultWarmupRounds)
    val measuredRounds = propertyInt("safezone.benchmark.runs", DefaultMeasuredRounds)

    println(Separator)
    println(s"Running $name")
    println(Separator)
    println(s"n range: [$minN, $maxN], step $stepN")
    println("Scenario: allocate+free 40 lists-of-lists where each structure has n lists and each list has n objects")
    println(s"Warmup rounds per n: $warmupRounds")
    println(s"Measured rounds per n: $measuredRounds")
    println()

    val buffer = scala.collection.mutable.ArrayBuffer.empty[SweepResult]
    var n = minN
    while (n <= maxN) {
      var warmup = 0
      while (warmup < warmupRounds) {
        runForN(n)
        warmup += 1
      }

      val samples = new Array[RunResult](measuredRounds)
      var round = 0
      while (round < measuredRounds) {
        val startTime = System.nanoTime()
        val checksum = runForN(n)
        val totalNs = System.nanoTime() - startTime
        samples(round) = RunResult(totalNs, checksum)
        round += 1
      }

      val checksums = samples.map(_.checksum)
      val expectedChecksum = checksums.head
      if (!checksums.forall(_ == expectedChecksum)) {
        val observed = checksums.mkString(", ")
        throw new IllegalStateException(s"Inconsistent checksum for n=$n across runs: $observed")
      }

      val meanNs = samples.map(_.totalNs).sum.toDouble / samples.length.toDouble
      val totalObjects = n.toLong * n.toLong * 40L
      buffer += SweepResult(n, totalObjects, meanNs, expectedChecksum)

      n += stepN
    }

    println(Separator)
    println("Results:")
    println(Separator)
    println(f"${"n"}%-8s ${"totalObjects"}%-15s ${"totalTime"}%-14s ${"checksum"}%-14s")
    buffer.foreach { row =>
      println(f"${row.n}%-8d ${row.totalObjects}%-15d ${formatNs(row.meanTotalNs)}%-14s ${row.checksum}%-14d")
    }
    println(Separator)
  }
}

@main def BenchmarkNoZonesRefactored(): Unit = {
  SafeZoneBenchmarksRefactored.runBenchmark("BenchmarkNoZones") { iterations =>
    var sum = 0L
    var i = 1L

    while (i <= iterations) {
      val iterationValue = new Counter((i % Int.MaxValue).toInt)
      sum += iterationValue.value.toLong
      i += 1
    }

    sum
  }
}

@main def BenchmarkSafeZoneThreadsRefactored(): Unit = {
  SafeZoneBenchmarksRefactored.runBenchmark("BenchmarkSafeZoneIteration") { iterations =>
    var sum = 0L
    var i = 1L

    while (i <= iterations) {
      SafeZone { sz ?=>
        val iterationValue = sz.alloc(new Counter((i % Int.MaxValue).toInt))
        sum += iterationValue.value.toLong
      }
      i += 1
    }

    sum
  }
}

@main def BenchmarkSafeZoneOuterRefactored(): Unit = {
  SafeZoneBenchmarksRefactored.runBenchmark("BenchmarkSafeZoneOutside") { iterations =>
    var sum = 0L

    SafeZone { sz ?=>
      var i = 1L
      while (i <= iterations) {
        val iterationValue = sz.alloc(new Counter((i % Int.MaxValue).toInt))
        sum += iterationValue.value.toLong
        i += 1
      }
    }

    sum
  }
}

@main def BenchmarkListOfListsNoZonesRefactored(): Unit = {
  def runBenchmarkNoZones(n: Int): Long = {
    final class BasicObject(val value: Int)
    final class InnerNode(val obj: BasicObject, val next: InnerNode)
    final class OuterNode(val inner: InnerNode, val next: OuterNode)

    def buildOneStructure(): OuterNode = {
      var outerHead: OuterNode = null
      var outer = 0
      while (outer < n) {
        var innerHead: InnerNode = null
        var inner = 0
        while (inner < n) {
          val obj = new BasicObject((outer + inner) & 0x7fffffff)
          innerHead = new InnerNode(obj, innerHead)
          inner += 1
        }
        outerHead = new OuterNode(innerHead, outerHead)
        outer += 1
      }
      outerHead
    }

    var checksum = 0L
    var structures = 0
    while (structures < 40) {
      var root = buildOneStructure()
      var outerCursor = root
      while (outerCursor != null) {
        var innerCursor = outerCursor.inner
        while (innerCursor != null) {
          checksum += innerCursor.obj.value.toLong
          innerCursor = innerCursor.next
        }
        outerCursor = outerCursor.next
      }
      root = null
      structures += 1
    }

    checksum
  }

  SafeZoneBenchmarksRefactored.runListOfListsSweepBenchmark("BenchmarkListOfListsNoZones")(runBenchmarkNoZones)
}

@main def BenchmarkListOfListsSafeZoneRefactored(): Unit = {
  def runBenchmarkSafeZone(n: Int): Long = {

      var checksum = 0L
      var structures = 0
      while (structures < 40) {
        SafeZone { sz ?=>
            final class BasicObject(val value: Int)
            final class InnerNode(val obj: BasicObject^{sz}, val next: InnerNode^{sz})
            final class OuterNode(val inner: InnerNode^{sz}, val next: OuterNode^{sz})

            def buildOneStructure(): OuterNode^{sz} = {
                var outerHead: OuterNode^{sz} = null
                var outer = 0
                while (outer < n) {
                var innerHead: InnerNode^{sz} = null
                var inner = 0
                while (inner < n) {
                    val obj = allocate(sz, new BasicObject((outer + inner) & 0x7fffffff))
                    innerHead = allocate(sz, new InnerNode(obj, innerHead))
                    inner += 1
                }
                outerHead = allocate(sz, new OuterNode(innerHead, outerHead))
                outer += 1
                }
                outerHead
            }
            var root = buildOneStructure()
            var outerCursor = root
            while (outerCursor != null) {
                var innerCursor = outerCursor.inner
                while (innerCursor != null) {
                    checksum += innerCursor.obj.value.toLong
                    innerCursor = innerCursor.next
                }
                outerCursor = outerCursor.next
            }
            root = null
            structures += 1
        }
      }

      checksum
  }

  SafeZoneBenchmarksRefactored.runListOfListsSweepBenchmark("BenchmarkListOfListsSafeZone")(runBenchmarkSafeZone)
}

@main def BenchmarkListOfListsAllRefactored(): Unit = {
  BenchmarkListOfListsNoZonesRefactored()
  BenchmarkListOfListsSafeZoneRefactored()
}


@main def All(): Unit = {
    BenchmarkNoZonesRefactored()
    // BenchmarkSafeZoneThreadsRefactored()
    BenchmarkSafeZoneOuterRefactored()
}