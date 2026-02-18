import scala.language.experimental.captureChecking

import scala.{Int, Double, Boolean, Unit, Array}
import java.lang.String

import scala.scalanative.memory.SafeZone
// import scala.scalanative.memory.SafeZoneTracing
import scala.scalanative.memory.SafeZone._
import scala.scalanative.runtime.SafeZoneAllocator.allocate

/**
 * Performance benchmarks for Safe Regions as described in the paper
 * "Safe Region for Scala Native" by Yawen Guan.
 * 
 * This benchmark suite tests:
 * 1. GC-only performance (baseline)
 * 2. SafeZone performance with different page sizes (2^12, 2^13, 2^16, 2^32 bytes)
 * 3. Extra tree-walking operations to stress the memory system
 */

// ============================================================================
// GC-Only Benchmarks (Baseline)
// ============================================================================

object GCBenchStandard {
  def run(input: String): Boolean = {
    class Node(var left: Node, var right: Node, var i: Int, var j: Int)
    val kStretchTreeDepth: Int   = 18 // about 16Mb
    val kLongLivedTreeDepth: Int = 16 // about 4Mb
    val kArraySize: Int          = 500000 // about 4Mb
    val kMinTreeDepth: Int       = 4
    val kMaxTreeDepth: Int       = 16

    def treeSize(i: Int): Int = ((1 << (i + 1)) - 1)
    def numIters(i: Int): Int = 2 * treeSize(kStretchTreeDepth) / treeSize(i)

    def populate(iDepth: Int, thisNode: Node): Unit =
      if (iDepth > 0) {
        thisNode.left = new Node(null, null, 0, 0)
        thisNode.right = new Node(null, null, 0, 0)
        populate(iDepth - 1, thisNode.left)
        populate(iDepth - 1, thisNode.right)
      }

    def makeTree(iDepth: Int): Node =
      if (iDepth <= 0) {
        new Node(null, null, 0, 0)
      } else {
        new Node(makeTree(iDepth - 1), makeTree(iDepth - 1), 0, 0)
      }

    def construction(depth: Int): Unit = {
      var tempTree: Node = null
      val iNumIter: Int  = numIters(depth)

      var i = 0
      while (i < iNumIter) {
        tempTree = new Node(null, null, 0, 0)
        populate(depth, tempTree)
        tempTree = null
        i += 1
      }

      i = 0
      while (i < iNumIter) {
        tempTree = makeTree(depth)
        tempTree = null
        i += 1
      }
    }

    var longLivedTree: Node = null
    var tempTree: Node      = null

    // Stretch the memory space quickly
    tempTree = makeTree(kStretchTreeDepth)
    tempTree = null

    // Create a long lived object
    longLivedTree = new Node(null, null, 0, 0)
    populate(kLongLivedTreeDepth, longLivedTree)

    // Create long-lived array, filling half of it
    val array = new Array[Double](kArraySize)
    var i     = 0
    while (i < kArraySize / 2) {
      array(i) = 1.0 / i
      i += 1
    }

    i = kMinTreeDepth
    while (i <= kMaxTreeDepth) {
      construction(i)
      i += 2
    }

    longLivedTree != null && array(1000) == 1.0 / 1000
  }
}

object GCBenchTreeWalking {
  def run(input: String): Boolean = {
    class Node(var left: Node, var right: Node, var i: Int, var j: Int)
    val kStretchTreeDepth: Int   = 18
    val kLongLivedTreeDepth: Int = 16
    val kArraySize: Int          = 500000
    val kMinTreeDepth: Int       = 4
    val kMaxTreeDepth: Int       = 16

    def treeSize(i: Int): Int = ((1 << (i + 1)) - 1)
    def numIters(i: Int): Int = 2 * treeSize(kStretchTreeDepth) / treeSize(i)

    // Extra tree-walking operation
    def walkTree(node: Node): Int = {
      if (node == null) 0
      else 1 + walkTree(node.left) + walkTree(node.right)
    }

    def populate(iDepth: Int, thisNode: Node): Unit =
      if (iDepth > 0) {
        thisNode.left = new Node(null, null, 0, 0)
        thisNode.right = new Node(null, null, 0, 0)
        populate(iDepth - 1, thisNode.left)
        populate(iDepth - 1, thisNode.right)
      }

    def makeTree(iDepth: Int): Node =
      if (iDepth <= 0) {
        new Node(null, null, 0, 0)
      } else {
        new Node(makeTree(iDepth - 1), makeTree(iDepth - 1), 0, 0)
      }

    def construction(depth: Int): Unit = {
      var tempTree: Node = null
      val iNumIter: Int  = numIters(depth)

      var i = 0
      while (i < iNumIter) {
        tempTree = new Node(null, null, 0, 0)
        populate(depth, tempTree)
        // Extra tree-walking operation
        walkTree(tempTree)
        tempTree = null
        i += 1
      }

      i = 0
      while (i < iNumIter) {
        tempTree = makeTree(depth)
        // Extra tree-walking operation
        walkTree(tempTree)
        tempTree = null
        i += 1
      }
    }

    var longLivedTree: Node = null
    var tempTree: Node      = null

    tempTree = makeTree(kStretchTreeDepth)
    tempTree = null

    longLivedTree = new Node(null, null, 0, 0)
    populate(kLongLivedTreeDepth, longLivedTree)

    val array = new Array[Double](kArraySize)
    var i     = 0
    while (i < kArraySize / 2) {
      array(i) = 1.0 / i
      i += 1
    }

    i = kMinTreeDepth
    while (i <= kMaxTreeDepth) {
      construction(i)
      i += 2
    }

    longLivedTree != null && array(1000) == 1.0 / 1000
  }
}

// ============================================================================
// SafeZone Benchmarks
// ============================================================================

object SafeZoneBenchStandard {
  def run(input: String): Boolean = {
    // SafeZoneTracing.init()
    val res = SafeZone { sz ?=>
      class Node(var left: Node^{sz}, var right: Node^{sz}, var i: Int, var j: Int)
      val kStretchTreeDepth: Int   = 18
      val kLongLivedTreeDepth: Int = 16
      val kArraySize: Int          = 500000
      val kMinTreeDepth: Int       = 4
      val kMaxTreeDepth: Int       = 16

      def treeSize(i: Int): Int = ((1 << (i + 1)) - 1)
      def numIters(i: Int): Int = 2 * treeSize(kStretchTreeDepth) / treeSize(i)

      def populate(iDepth: Int, thisNode: Node^{sz}): Unit =
        if (iDepth > 0) {
          thisNode.left = alloc(new Node(null, null, 0, 0))
          thisNode.right = alloc(new Node(null, null, 0, 0))
          populate(iDepth - 1, thisNode.left)
          populate(iDepth - 1, thisNode.right)
        }

      def makeTree(iDepth: Int): Node^{sz} =
        if (iDepth <= 0) {
          alloc(new Node(null, null, 0, 0))
        } else {
          allocate(sz, new Node(makeTree(iDepth - 1), makeTree(iDepth - 1), 0, 0))
        }

      def construction(depth: Int): Unit = {
        var tempTree: Node^{sz} = null
        val iNumIter: Int  = numIters(depth)

        var i = 0
        while (i < iNumIter) {
          tempTree = alloc(new Node(null, null, 0, 0))
          populate(depth, tempTree)
          tempTree = null
          i += 1
        }

        i = 0
        while (i < iNumIter) {
          tempTree = makeTree(depth)
          tempTree = null
          i += 1
        }
      }

      var longLivedTree: Node^{sz} = null
      var tempTree: Node^{sz}      = null

      tempTree = makeTree(kStretchTreeDepth)
      tempTree = null

      longLivedTree = alloc(new Node(null, null, 0, 0))
      populate(kLongLivedTreeDepth, longLivedTree)

      case class DoubleWrapper(value: Double)
      val array = allocate(sz, new Array[DoubleWrapper^{sz}](kArraySize))
      var i     = 0
      while (i < kArraySize / 2) {
        array(i) = alloc(new DoubleWrapper(1.0 / i))
        i += 1
      }

      i = kMinTreeDepth
      while (i <= kMaxTreeDepth) {
        construction(i)
        i += 2
      }

      longLivedTree != null && array(1000).value == 1.0 / 1000
    }
    // SafeZoneTracing.printStats()
    res
  }
}

object SafeZoneBenchTreeWalking {
  def run(input: String): Boolean = {
    // SafeZoneTracing.init()
    val res = SafeZone { sz ?=>
      class Node(var left: Node^{sz}, var right: Node^{sz}, var i: Int, var j: Int)
      val kStretchTreeDepth: Int   = 18
      val kLongLivedTreeDepth: Int = 16
      val kArraySize: Int          = 500000
      val kMinTreeDepth: Int       = 4
      val kMaxTreeDepth: Int       = 16

      def treeSize(i: Int): Int = ((1 << (i + 1)) - 1)
      def numIters(i: Int): Int = 2 * treeSize(kStretchTreeDepth) / treeSize(i)

      // Extra tree-walking operation
      def walkTree(node: Node^{sz}): Int = {
        if (node == null) 0
        else 1 + walkTree(node.left) + walkTree(node.right)
      }

      def populate(iDepth: Int, thisNode: Node^{sz}): Unit =
        if (iDepth > 0) {
          thisNode.left = alloc(new Node(null, null, 0, 0))
          thisNode.right = alloc(new Node(null, null, 0, 0))
          populate(iDepth - 1, thisNode.left)
          populate(iDepth - 1, thisNode.right)
        }

      def makeTree(iDepth: Int): Node^{sz} =
        if (iDepth <= 0) {
          alloc(new Node(null, null, 0, 0))
        } else {
          allocate(sz, new Node(makeTree(iDepth - 1), makeTree(iDepth - 1), 0, 0))
        }

      def construction(depth: Int): Unit = {
        var tempTree: Node^{sz} = null
        val iNumIter: Int  = numIters(depth)

        var i = 0
        while (i < iNumIter) {
          tempTree = alloc(new Node(null, null, 0, 0))
          populate(depth, tempTree)
          // Extra tree-walking operation
          walkTree(tempTree)
          tempTree = null
          i += 1
        }

        i = 0
        while (i < iNumIter) {
          tempTree = makeTree(depth)
          // Extra tree-walking operation
          walkTree(tempTree)
          tempTree = null
          i += 1
        }
      }

      var longLivedTree: Node^{sz} = null
      var tempTree: Node^{sz}      = null

      tempTree = makeTree(kStretchTreeDepth)
      tempTree = null

      longLivedTree = alloc(new Node(null, null, 0, 0))
      populate(kLongLivedTreeDepth, longLivedTree)

      case class DoubleWrapper(value: Double)
      val array = allocate(sz, new Array[DoubleWrapper^{sz}](kArraySize))
      var i     = 0
      while (i < kArraySize / 2) {
        array(i) = alloc(new DoubleWrapper(1.0 / i))
        i += 1
      }

      i = kMinTreeDepth
      while (i <= kMaxTreeDepth) {
        construction(i)
        i += 2
      }

      longLivedTree != null && array(1000).value == 1.0 / 1000
    }
    // SafeZoneTracing.printStats()
    res
  }
}

// ============================================================================
// Main Test Runners
// ============================================================================

@main def TestAllBenchmarks() = {
  println("=" * 80)
  println("Safe Region Performance Benchmarks")
  println("Based on: Safe Region for Scala Native by Yawen Guan")
  println("=" * 80)
  println()
  
  // Run all benchmarks as described in the paper (20 iterations each)
  val numRuns = 20
  
  println("\n[1/4] GC-Only Baseline (Standard Operations)")
  println("-" * 80)
  BenchmarkRunner.runBenchmark("GC Only - Standard", numRuns)(GCBenchStandard.run)
  
  println("\n[2/4] SafeZone (Standard Operations)")
  println("-" * 80)
  println("Note: Test with different page sizes by configuring SafeZone allocator")
  println("      Page sizes tested in paper: 2^12, 2^13, 2^16, 2^32 bytes")
  BenchmarkRunner.runBenchmark("SafeZone - Standard", numRuns)(SafeZoneBenchStandard.run)
  
  println("\n[3/4] GC-Only (Extra Tree-Walking)")
  println("-" * 80)
  BenchmarkRunner.runBenchmark("GC Only - Tree Walking", numRuns)(GCBenchTreeWalking.run)
  
  println("\n[4/4] SafeZone (Extra Tree-Walking)")
  println("-" * 80)
  BenchmarkRunner.runBenchmark("SafeZone - Tree Walking", numRuns)(SafeZoneBenchTreeWalking.run)
  
  println("\n" + "=" * 80)
  println("Benchmark Suite Complete")
  println("=" * 80)
}

@main def TestGCOnly() = {
  println("Running GC-Only benchmarks...")
  BenchmarkRunner.runBenchmark("GC Only - Standard", 20)(GCBenchStandard.run)
}

@main def TestGCTreeWalking() = {
  println("Running GC-Only with tree-walking benchmarks...")
  BenchmarkRunner.runBenchmark("GC Only - Tree Walking", 20)(GCBenchTreeWalking.run)
}

@main def TestSafeZoneStandard() = {
  println("Running SafeZone standard benchmarks...")
  BenchmarkRunner.runBenchmark("SafeZone - Standard", 20)(SafeZoneBenchStandard.run)
}

@main def TestSafeZoneTreeWalking() = {
  println("Running SafeZone with tree-walking benchmarks...")
  BenchmarkRunner.runBenchmark("SafeZone - Tree Walking", 20)(SafeZoneBenchTreeWalking.run)
}

// Benchmark runner (reused from BenchOrig.scala)
object BenchmarkRunner {
  def runBenchmark(name: String, numRuns: Int)(runFn: String => Boolean): Unit = {
    val times = new Array[Double](numRuns)
    
    println(s"Running $name benchmark $numRuns times...")
    
    var i = 0
    while (i < numRuns) {
      val startTime = System.nanoTime()
      val result = runFn("")
      val endTime = System.nanoTime()
      val durationMs = (endTime - startTime) / 1000000.0
      times(i) = durationMs
      
      println(s"  Run ${i + 1}: $durationMs ms (result: $result)")
      i += 1
    }
    
    // Calculate statistics
    var sum = 0.0
    var min = times(0)
    var max = times(0)
    i = 0
    while (i < numRuns) {
      sum += times(i)
      if (times(i) < min) min = times(i)
      if (times(i) > max) max = times(i)
      i += 1
    }
    val avg = sum / numRuns
    
    println(s"\nStatistics for $name:")
    println(s"  Average: $avg ms")
    println(s"  Min:     $min ms")
    println(s"  Max:     $max ms")
    
    // Calculate and display improvement if SafeZone benchmark
    if (name.contains("SafeZone")) {
      println(s"\nNote: Compare with GC-Only baseline to calculate performance improvement")
    }
  }
}
