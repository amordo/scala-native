package scala.scalanative.memory

import scala.scalanative.unsafe._
import scala.scalanative.runtime.{fromRawPtr, RawPtr}

/** Performance tracing utilities for SafeZone operations.
 *  
 *  This object provides methods to measure and report performance
 *  statistics for SafeZone allocations. Tracing must be enabled
 *  at compile time by defining ZONE_ENABLE_TRACING.
 *  
 *  Example usage:
 *  {{{
 *  SafeZoneTracing.init()
 *  
 *  SafeZone { sz =>
 *    given SafeZone = sz
 *    // ... perform allocations ...
 *  }
 *  
 *  SafeZoneTracing.printStats()
 *  }}}
 */
object SafeZoneTracing {
  
  /** Initialize tracing. This resets all statistics to zero.
   *  Note: Only has effect if ZONE_ENABLE_TRACING is defined at compile time.
   */
  def init(): Unit = Impl.zone_tracing_init()
  
  /** Print accumulated performance statistics to stderr.
   *  This includes operation counts, memory usage, and timing information.
   *  Note: Only has effect if ZONE_ENABLE_TRACING is defined at compile time.
   */
  def printStats(): Unit = Impl.zone_tracing_print_stats()
  
  /** Reset all statistics to zero without printing.
   *  Note: Only has effect if ZONE_ENABLE_TRACING is defined at compile time.
   */ 
  def reset(): Unit = Impl.zone_tracing_reset()
  
  @extern
  @define("__SCALANATIVE_MEMORY_SAFEZONE")
  private object Impl {
    @name("zone_tracing_init")
    def zone_tracing_init(): Unit = extern
    
    @name("zone_tracing_print_stats")
    def zone_tracing_print_stats(): Unit = extern
    
    @name("zone_tracing_reset")
    def zone_tracing_reset(): Unit = extern
  }
}
