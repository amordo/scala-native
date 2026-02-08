import scala.language.experimental.captureChecking

import scala.{Int, Double, Boolean, Unit, Array}
import java.lang.String

import scala.scalanative.memory.SafeZone
import scala.scalanative.memory.SafeZone._
import scala.scalanative.runtime.SafeZoneAllocator.allocate


object GCBenchBenchmarkZones {
  def run(input: String): Boolean = {
    SafeZone { sz ?=>
      class Node(var left: Node^{sz}, var right: Node^{sz}, var i: Int, var j: Int)
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
      def populate(iDepth: Int, thisNode: Node^{sz}): Unit =
        if (iDepth > 0) {
          thisNode.left = alloc(new Node(null, null, 0, 0))
          thisNode.right = alloc(new Node(null, null, 0, 0))
          populate(iDepth - 1, thisNode.left)
          populate(iDepth - 1, thisNode.right)
        }

      // Build tree bottom-up
      def makeTree(iDepth: Int): Node^{sz} =
        if (iDepth <= 0) {
          alloc(new Node(null, null, 0, 0))
        } else {
          allocate(sz, new Node(makeTree(iDepth - 1), makeTree(iDepth - 1), 0, 0))
        }

      def construction(depth: Int): Unit = {
        var root: Node^{sz}     = null
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

      // // Start method logic inlined
      var root: Node^{sz}          = null
      var longLivedTree: Node^{sz} = null
      var tempTree: Node^{sz}      = null

      // // Stretch the memory space quickly
      tempTree = makeTree(kStretchTreeDepth)
      tempTree = null

      // Create a long lived object
      longLivedTree = alloc(new Node(null, null, 0, 0))
      populate(kLongLivedTreeDepth, longLivedTree)

      // // Create long-lived array, filling half of it
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

      // Return the result
      longLivedTree != null && array(1000).value == 1.0 / 1000
    }
  }

}

@main def TestGCBenchBenchmarkZones() = {
  BenchmarkRunner.runBenchmark("GCBench SafeZone", 5)(GCBenchBenchmarkZones.run)
}