import scala.language.experimental.captureChecking

import scala.{Int, Double, Boolean, Unit, Array}
import java.lang.String

object GCBenchBenchmarkOrig {
  def run(input: String): Boolean = {
      class Node(var left: Node, var right: Node, var i: Int, var j: Int)
      val kStretchTreeDepth: Int   = 18 // about 16Mb
      val kLongLivedTreeDepth: Int = 16 // about 4Mb
      val kArraySize: Int          = 500000 // about 4Mb
      val kMinTreeDepth: Int       = 4
      val kMaxTreeDepth: Int       = 16

      // Nodes used by a tree of a given size
      def treeSize(i: Int): Int = ((1 << (i + 1)) - 1)

      // Number of iterations to use for a given tree depth
      def numIters(i: Int): Int = 2 * treeSize(kStretchTreeDepth) / treeSize(i)

      // // Build tree top down, assigning to older objects.
      def populate(iDepth: Int, thisNode: Node): Unit =
        if (iDepth > 0) {
          thisNode.left = new Node(null, null, 0, 0)
          thisNode.right = new Node(null, null, 0, 0)
          populate(iDepth - 1, thisNode.left)
          populate(iDepth - 1, thisNode.right)
        }

      // Build tree bottom-up
      def makeTree(iDepth: Int): Node =
        if (iDepth <= 0) {
          new Node(null, null, 0, 0)
        } else {
          new Node(makeTree(iDepth - 1), makeTree(iDepth - 1), 0, 0)
        }

      def construction(depth: Int): Unit = {
        var root: Node     = null
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

      // // Start method logic inlined
      var root: Node          = null
      var longLivedTree: Node = null
      var tempTree: Node      = null

      // // Stretch the memory space quickly
      tempTree = makeTree(kStretchTreeDepth)
      tempTree = null

      // Create a long lived object
      longLivedTree = new Node(null, null, 0, 0)
      populate(kLongLivedTreeDepth, longLivedTree)

      // // Create long-lived array, filling half of it
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

      // Return the result
      longLivedTree != null && array(1000) == 1.0 / 1000
  }
}

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
      
      println(s"Run ${i + 1}: $durationMs ms (result: $result)")
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
    
    println(s"\nStatistics:")
    println(s"  Average: $avg ms")
    println(s"  Min: $min ms")
    println(s"  Max: $max ms")
  }
}

@main def TestGCBenchBenchmarkOriginal() = {
  GCBenchBenchmarkOrig.run("")
}