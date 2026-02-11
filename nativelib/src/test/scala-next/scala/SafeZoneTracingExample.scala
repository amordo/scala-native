package scala.scalanative.memory

import scala.scalanative.memory.{SafeZone, SafeZoneTracing}

/** Example demonstrating SafeZone performance tracing.
 *  
 *  To see tracing output, compile with ZONE_ENABLE_TRACING defined.
 *  
 *  Compile:
 *    sbt 'set nativeConfig ~= (_.withCompileOptions(Seq("-DZONE_ENABLE_TRACING")))' run
 *  
 *  Or modify ZoneTracing.h to uncomment #define ZONE_ENABLE_TRACING
 */
object SafeZoneTracingExample {
  
  class Point(val x: Int, val y: Int) {
    def distanceSquared: Int = x * x + y * y
  }
  
  class Rectangle(val width: Int, val height: Int) {
    def area: Int = width * height
  }
  
  def main(args: Array[String]): Unit = {
    println("SafeZone Performance Tracing Example")
    println("=" * 50)
    
    // Initialize tracing (resets all counters)
    SafeZoneTracing.init()
    
    // Example 1: Small allocations
    println("\n1. Testing small object allocations...")
    SafeZone { sz =>
      given SafeZone = sz
      
      for (i <- 0 until 1000) {
        val p = sz.alloc(new Point(i, i * 2))
        val _ = p.distanceSquared // Use the object
      }
    }
    
    // Example 2: Mixed size allocations  
    println("\n2. Testing mixed size allocations...")
    SafeZone { sz =>
      given SafeZone = sz
      
      for (i <- 0 until 500) {
        val p = sz.alloc(new Point(i, i))
        val r = sz.alloc(new Rectangle(i * 10, i * 20))
        val _ = (p.distanceSquared, r.area)
      }
    }
    
    // Example 3: Nested zones
    println("\n3. Testing nested zone allocations...")
    SafeZone { outerZone =>
      given outer: SafeZone = outerZone
      
      for (i <- 0 until 100) {
        val p1 = outer.alloc(new Point(i, i))
        
        SafeZone { innerZone =>
          given inner: SafeZone = innerZone
          
          for (j <- 0 until 10) {
            val p2 = inner.alloc(new Point(i + j, i * j))
            val _ = p2.distanceSquared
          }
        }
        
        val _ = p1.distanceSquared
      }
    }
    
    // Example 4: Large number of allocations
    println("\n4. Testing high-volume allocations...")
    val iterations = 10000
    SafeZone { sz =>
      given SafeZone = sz
      
      for (i <- 0 until iterations) {
        val p = sz.alloc(new Point(i % 100, i % 200))
        if (i % 1000 == 0 && i > 0) {
          // Periodic checkpoint (just to show progress)
          print(".")
        }
      }
    }
    println()
    
    // Print comprehensive statistics
    println("\n" + "=" * 50)
    println("Performance Statistics:")
    println("=" * 50)
    SafeZoneTracing.printStats()
    
    // Reset and measure a single operation
    println("\n" + "=" * 50)
    println("Single Zone Benchmark:")
    println("=" * 50)
    SafeZoneTracing.reset()
    
    SafeZone { sz =>
      given SafeZone = sz
      for (i <- 0 until 10000) {
        val _ = sz.alloc(new Point(i, i))
      }
    }
    
    SafeZoneTracing.printStats()
    
    println("\nExample completed!")
  }
}
