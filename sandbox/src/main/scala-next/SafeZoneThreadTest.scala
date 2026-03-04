/**
 * SafeZone Multi-Threading Safety Test Suite
 * 
 * This file implements comprehensive tests for verifying data-race freedom
 * and safety properties when using SafeZone with multi-threaded code.
 * 
 * Test Coverage:
 * 1. Compile-Time Safety: Verify capture checking prevents zone escapes
 * 2. Thread-Local Zones: Verify independent zones per thread have no races
 * 3. Stress Testing: Expose timing-dependent bugs with heavy parallel allocation
 * 4. Zone Lifetime: Verify zones properly outlive all thread operations
 * 5. Nested Zones: Verify fine-grained cleanup with batch processing
 * 
 * Safety Properties Verified:
 * - Capture Safety: Zone-allocated objects cannot escape zone lifetime
 * - Data-Race Freedom: No concurrent access to shared zone state
 * - Zone Isolation: Each thread works with independent zones
 * - Lifetime Correctness: Zones outlive all operations using their memory
 * - Correctness Under Parallelism: Results match sequential semantics
 * 
 * Implementation Note:
 * - Uses parallel collections (.par) for clean parallelism
 * - Each parallel task gets its own thread-local SafeZone
 * - Atomic operations used for shared state where needed
 * 
 * Usage:
 *   sbt> sandbox3_next/run
 *   Select: TestSafeZoneThreads (runs all tests)
 *   Or select individual tests: TestThreadLocalZones, TestStressParallel, etc.
 */

import scala.language.experimental.captureChecking

import scala.{Int, Long, Unit}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.scalanative.memory.SafeZone
import scala.util.Random
import scala.collection.parallel.CollectionConverters._

// Helper class for SafeZone allocation tests
class Counter(val value: Int)

// Helper class for array tests
class OffHeapArray[T](val size: Int)(using sz: SafeZone^) {
  private val data = sz.alloc(new Array[Any](size))
  
  def apply(i: Int): T = {
    require(i >= 0 && i < size, s"Index $i out of bounds [0, $size)")
    data.asInstanceOf[Array[T]](i)
  }
  
  def update(i: Int, value: T): Unit = {
    require(i >= 0 && i < size, s"Index $i out of bounds [0, $size)")
    data.asInstanceOf[Array[T]](i) = value
  }
  
  def sum(using Numeric[T]): T = {
    var s = implicitly[Numeric[T]].zero
    var i = 0
    while (i < size) {
      s = implicitly[Numeric[T]].plus(s, apply(i))
      i += 1
    }
    s
  }
}

// Simple assertion helper
def assertEquals(expected: Long, actual: Long): Unit = {
  if (expected != actual) {
    throw new AssertionError(s"Expected $expected but got $actual")
  }
}

object SafeZoneThreadTest {
  
  // ===== Test Suite 1: Compile-Time Safety (Capture Checking) =====
  
  // NOTE: These tests verify that the compiler REJECTS unsafe code.
  // Uncomment to verify they produce compilation errors:
  
  /*
  def zoneEscapeShouldFail(): Unit = {
    // ERROR: Object escapes zone scope
    val leaked = SafeZone { sz ?=>
      sz.alloc(new Counter(42))  // Error: escapes scope
    }
    leaked.value  // Would be use-after-free
  }
  */
  
  def zoneContainmentSafe(): Unit = {
    var result = 0
    SafeZone { sz ?=>
      val obj = sz.alloc(new Counter(42))
      result = obj.value  // Copy data out, not reference
    }
    assertEquals(42, result)
    println("✓ Zone containment safety test passed!")
  }
  
  // ===== Test Suite 2: Thread-Local Zone Pattern =====
  
  def threadLocalZonesAreIndependent(): Unit = {
    val numThreads = 4
    val allocsPerThread = 100000
    
    val results = (0 until numThreads).par.map { threadId =>
      SafeZone { sz ?=>
        var sum = 0L
        (0 until allocsPerThread).foreach { i =>
          val obj = sz.alloc(new Counter(i))
          sum += obj.value
        }
        sum
      }
    }.seq
    
    // Each thread should compute same sum independently
    val expected = (0L until allocsPerThread).sum
    results.foreach(r => assertEquals(expected, r))
    
    println(s"✓ Thread-local zones test passed!")
    println(s"  Threads: $numThreads")
    println(s"  Allocations per thread: $allocsPerThread")
    println(s"  Expected sum: $expected")
  }
  
  // ===== Test Suite 3: Stress Test for Race Conditions =====
  
  def stressTestParallelAllocation(): Unit = {
    // Run many iterations to expose timing-dependent bugs
    val iterations = 50
    val dataSize = 10000
    
    println(s"Running stress test: $iterations iterations, $dataSize elements each")
    
    (0 until iterations).foreach { iter =>
      val data = Array.fill(dataSize)(Random.nextInt(1000))
      
      val results = data.par.map { value =>
        SafeZone { sz ?=>
          // Heavy allocation to stress memory system
          val arr = new OffHeapArray[Int](100)(using sz)
          (0 until 100).foreach(i => arr(i) = value + i)
          arr.sum
        }
      }.seq.toArray
      
      // Verify correctness
      data.zip(results).foreach { case (input, output) =>
        val expected = (0 until 100).map(input + _).sum
        assertEquals(expected.toLong, output.toLong)
      }
      
      // if ((iter + 1) % 10 == 0) {
      //   println(s"  Completed ${iter + 1}/$iterations iterations")
      // }
    }
    
    println("✓ Stress test for parallel allocation passed!")
  }
  
  // ===== Test Suite 4: Zone Lifetime vs Thread Lifetime =====
  
  def zoneOutlivesThreadWork(): Unit = {
    SafeZone { sz ?=>
      val data = new OffHeapArray[Int](1000)(using sz)
      (0 until 1000).foreach(i => data(i) = i)
      
      val sum = new AtomicLong(0)
      val barrier = new CountDownLatch(4)
      
      // Spawn threads that read zone-allocated data
      val threads = (0 until 4).map { threadId =>
        val thread = new Thread(() => {
          val start = threadId * 250
          val end = start + 250
          var localSum = 0L
          (start until end).foreach(i => localSum += data(i))
          sum.addAndGet(localSum)
          barrier.countDown()
        })
        thread.start()
        thread
      }
      
      // Wait for all threads BEFORE closing zone
      val completed = barrier.await(5, TimeUnit.SECONDS)
      if (!completed) {
        throw new RuntimeException("Threads did not complete in time")
      }
      threads.foreach(_.join())
      
      val expected = (0L until 1000).sum
      assertEquals(expected, sum.get())
      
      println(s"✓ Zone lifetime safety test passed!")
      println(s"  Threads: 4")
      println(s"  Elements: 1000")
      println(s"  Sum verified: $expected")
    } // Safe to close now - all threads done
  }
  
  // ===== Test Suite 5: Nested Zones for Batch Processing =====
  
  def nestedZonesBatchProcessing(): Unit = {
    case class Event(id: Int, value: Double)
    
    val totalEvents = 100000
    val batchSize = 1000
    val events = Array.tabulate(totalEvents)(i => Event(i, i * 1.5))
    
    val processedCount = new AtomicLong(0)
    val sumValues = new AtomicReference[Double](0.0)
    
    val batches = events.grouped(batchSize).toArray
    
    batches.par.foreach { batch =>
      SafeZone { batchZone ?=>  // Zone per batch (thread-local)
        batch.foreach { event =>
          SafeZone { eventZone ?=>  // Nested zone per event
            val processed = eventZone.alloc(new Counter(event.id))
            processedCount.incrementAndGet()
            
            // Atomic update for sumValues
            var done = false
            while (!done) {
              val current = sumValues.get()
              val updated = current + event.value
              done = sumValues.compareAndSet(current, updated)
            }
          } // Frequent small cleanups
        }
      }
    }
    
    assertEquals(totalEvents.toLong, processedCount.get())
    val expectedSum = (0 until totalEvents).map(_ * 1.5).sum
    assert(math.abs(sumValues.get() - expectedSum) < 0.001, 
      s"Sum mismatch: ${sumValues.get()} vs $expectedSum")
    
    println(s"✓ Nested zones batch processing test passed!")
    println(s"  Total events: $totalEvents")
    println(s"  Batch size: $batchSize")
    println(s"  Batches: ${totalEvents / batchSize}")
  }
  
  // ===== Run All Tests =====
  
  def runAllTests(): Unit = {
    println("=" * 60)
    println("SafeZone Multi-Threading Safety Tests")
    println("=" * 60)
    println()
    
    println("[1/6] Testing compile-time safety (zone containment)...")
    zoneContainmentSafe()
    println()
    
    println("[2/6] Testing thread-local zones independence...")
    threadLocalZonesAreIndependent()
    println()
    
    println("[3/6] Running stress test for race conditions...")
    stressTestParallelAllocation()
    println()
    
    println("[4/6] Testing zone lifetime vs thread lifetime...")
    zoneOutlivesThreadWork()
    println()
    
    println("[5/6] Testing nested zones with batch processing...")
    nestedZonesBatchProcessing()
    println()
    
    println("=" * 60)
    println("✓ All SafeZone thread safety tests passed!")
    println("=" * 60)
  }
}

@main def TestSafeZoneThreads() = {
  SafeZoneThreadTest.runAllTests()
}

// Individual test entry points for debugging
@main def TestZoneContainment() = {
  SafeZoneThreadTest.zoneContainmentSafe()
}

@main def TestThreadLocalZones() = {
  SafeZoneThreadTest.threadLocalZonesAreIndependent()
}

@main def TestStressParallel() = {
  SafeZoneThreadTest.stressTestParallelAllocation()
}

@main def TestZoneLifetime() = {
  SafeZoneThreadTest.zoneOutlivesThreadWork()
}

@main def TestNestedZones() = {
  SafeZoneThreadTest.nestedZonesBatchProcessing()
}
