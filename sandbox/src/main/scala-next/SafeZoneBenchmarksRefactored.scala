import scala.language.experimental.captureChecking

import scala.scalanative.memory.SafeZone

object SafeZoneBenchmarksRefactored {
  final case class RunResult(totalNs: Long, checksum: Long)

  private val DefaultIterations = 100_000_000_000L
  private val DefaultWarmupRounds = 3
  private val DefaultMeasuredRounds = 5
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


@main def All(): Unit = {
    BenchmarkNoZonesRefactored()
    // BenchmarkSafeZoneThreadsRefactored()
    BenchmarkSafeZoneOuterRefactored()
}