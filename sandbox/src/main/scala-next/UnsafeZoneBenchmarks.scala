import scala.scalanative.unsafe._

/* Unsafe Zone benchmarks, mirroring SafeZoneBenchmarksRefactored.
 *
 * Since Zone.alloc allocates raw bytes (Ptr[Byte]) rather than Scala objects,
 * node data is represented as CStructs allocated with alloc[T]() in a Zone scope.
 */

@main def BenchmarkUnsafeZoneIterations(): Unit = {
  SafeZoneBenchmarksRefactored.runBenchmark("BenchmarkUnsafeZoneIteration") { iterations =>
    type CounterT = CStruct1[CInt]
    var sum = 0L
    var i = 1L

    while (i <= iterations) {
      Zone.acquire { implicit z =>
        val counter = alloc[CounterT]()
        counter._1 = (i % Int.MaxValue).toInt
        sum += counter._1.toLong
      }
      i += 1
    }

    sum
  }
}

@main def BenchmarkUnsafeZoneOuter(): Unit = {
  SafeZoneBenchmarksRefactored.runBenchmark("BenchmarkUnsafeZoneOuter") { iterations =>
    var sum = 0L

    Zone.acquire { implicit z =>
      type CounterT = CStruct1[CInt]
      var i = 1L
      while (i <= iterations) {
        val counter = alloc[CounterT]()
        counter._1 = (i % Int.MaxValue).toInt
        sum += counter._1.toLong
        i += 1
      }
    }

    sum
  }
}

@main def BenchmarkListOfListsUnsafeZone(): Unit = {
  // InnerNode: _1 = Int value, _2 = Ptr[Byte] (next InnerNode, cast on use)
  // OuterNode: _1 = Ptr[Byte] (inner list head), _2 = Ptr[Byte] (next OuterNode, cast on use)
  type InnerNode = CStruct2[CInt, Ptr[Byte]]
  type OuterNode = CStruct2[Ptr[Byte], Ptr[Byte]]

  def runForN(n: Int): Long = {
    var checksum = 0L
    var structures = 0

    while (structures < 40) {
      Zone.acquire { implicit z =>
        var outerHead: Ptr[OuterNode] = null

        var outer = 0
        while (outer < n) {
          var innerHead: Ptr[InnerNode] = null

          var inner = 0
          while (inner < n) {
            val node = alloc[InnerNode]()
            node._1 = (outer + inner) & 0x7fffffff
            node._2 = innerHead.asInstanceOf[Ptr[Byte]]
            innerHead = node
            inner += 1
          }

          val outerNode = alloc[OuterNode]()
          outerNode._1 = innerHead.asInstanceOf[Ptr[Byte]]
          outerNode._2 = outerHead.asInstanceOf[Ptr[Byte]]
          outerHead = outerNode
          outer += 1
        }

        var outerCursor = outerHead
        while (outerCursor != null) {
          var innerCursor = outerCursor._1.asInstanceOf[Ptr[InnerNode]]
          while (innerCursor != null) {
            checksum += innerCursor._1.toLong
            innerCursor = innerCursor._2.asInstanceOf[Ptr[InnerNode]]
          }
          outerCursor = outerCursor._2.asInstanceOf[Ptr[OuterNode]]
        }
      }
      structures += 1
    }

    checksum
  }

  SafeZoneBenchmarksRefactored.runListOfListsSweepBenchmark("BenchmarkListOfListsUnsafeZone")(runForN)
}

@main def BenchmarkListOfListsAllUnsafeZone(): Unit = {
  BenchmarkListOfListsNoZonesRefactored()
  BenchmarkListOfListsUnsafeZone()
}

@main def AllUnsafeZone(): Unit = {
  BenchmarkUnsafeZoneIterations()
  BenchmarkUnsafeZoneOuter()
}
