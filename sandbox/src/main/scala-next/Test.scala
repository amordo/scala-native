package scala.scalanative.benchmarks

import scala.language.experimental.captureChecking
import scala.scalanative.unsafe._
import scala.scalanative.memory.SafeZone
import scala.scalanative.runtime.SafeZoneAllocator.allocate

object RegionAllocationBenchmarkHelpers {

  final class Node(val value: Int)
  type ZoneNode = CStruct1[Int]

  def buildRegular(n: Int): Array[Array[Node]] = {
    val outer = new Array[Array[Node]](n)
    var i = 0
    while (i < n) {
      val inner = new Array[Node](n)
      var j = 0
      while (j < n) {
        inner(j) = new Node(j)
        j += 1
      }
      outer(i) = inner
      i += 1
    }
    outer
  }

  def consume(arr: Array[Array[Node]]): Long = {
    var sum: Long = 0L
    var i = 0
    while (i < arr.length) {
      val inner = arr(i)
      var j = 0
      while (j < inner.length) {
        sum += inner(j).value.toLong
        j += 1
      }
      i += 1
    }
    sum
  }

  def buildSafeAndConsume(n: Int): Long =
    SafeZone { sz ?=>
      val outer: Array[Array[Node^{sz}]^{sz}]^{sz} =
        allocate(sz, new Array[Array[Node^{sz}]^{sz}](n))
      var i = 0
      while (i < n) {
        val inner: Array[Node^{sz}]^{sz} =
          allocate(sz, new Array[Node^{sz}](n))
        var j = 0
        while (j < n) {
          inner(j) = allocate(sz, new Node(j))
          j += 1
        }
        outer(i) = inner
        i += 1
      }

      var sum: Long = 0L
      var outerIndex = 0
      while (outerIndex < outer.length) {
        val inner = outer(outerIndex)
        var j = 0
        while (j < inner.length) {
          sum += inner(j).value.toLong
          j += 1
        }
        outerIndex += 1
      }
      sum
    }

  def buildZoneAndConsume(n: Int): Long =
    Zone.acquire { implicit z =>
      val outer: Ptr[Ptr[ZoneNode]] = alloc[Ptr[ZoneNode]](n)
      var i = 0
      while (i < n) {
        val inner: Ptr[ZoneNode] = alloc[ZoneNode](n)
        var j = 0
        while (j < n) {
          inner(j)._1 = j
          j += 1
        }
        outer(i) = inner
        i += 1
      }

      var sum: Long = 0L
      var outerIndex = 0
      while (outerIndex < n) {
        val inner = outer(outerIndex)
        var j = 0
        while (j < n) {
          sum += inner(j)._1.toLong
          j += 1
        }
        outerIndex += 1
      }
      sum
    }

  def timeMs(label: String)(f: => Long): Unit = {
    val start = System.nanoTime()
    val result: Long = f
    val end = System.nanoTime()
    val ms = (end - start) / 1e6
    println(f"$label%-24s ${ms}%.2f ms (checksum=$result)")
  }
}

object RegularBenchmark {
  import RegionAllocationBenchmarkHelpers._

  def main(args: Array[String]): Unit = {
    val ns = Array(500, 1000, 1500, 2000, 2500, 3000)
    ns.foreach { n =>
      println(s"\n== n = $n ==")
      timeMs("regular") {
        var total: Long = 0L
        var iter = 0
        while (iter < 40) {
          total += consume(buildRegular(n))
          iter += 1
        }
        total
      }
    }
  }
}

object ZoneBenchmark {
  import RegionAllocationBenchmarkHelpers._

  def main(args: Array[String]): Unit = {
    val ns = Array(500, 1000, 1500, 2000, 2500, 3000)
    ns.foreach { n =>
      println(s"\n== n = $n ==")
      timeMs("Zone") {
        var total: Long = 0L
        var iter = 0
        while (iter < 40) {
          total += buildZoneAndConsume(n)
          iter += 1
        }
        total
      }
    }
  }
}

object SafeZoneBenchmark {
  import RegionAllocationBenchmarkHelpers._

  def main(args: Array[String]): Unit = {
    val ns = Array(500, 1000, 1500, 2000, 2500, 3000)
    ns.foreach { n =>
      println(s"\n== n = $n ==")
      timeMs("SafeZone") {
        var total: Long = 0L
        var iter = 0
        while (iter < 40) {
          total += buildSafeAndConsume(n)
          iter += 1
        }
        total
      }
    }
  }
}