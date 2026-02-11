# SafeZone Performance Tracing

This directory contains performance tracing functionality for the SafeZone feature introduced in commit 8388c31e769d33fbcaa2e4c23ec63786f9072e5c.

## Files Added

- **ZoneTracing.h** - Header file with tracing macros and statistics structure
- **ZoneTracing.c** - Implementation of tracing functions and statistics reporting
- **SafeZoneTracing.scala** - Scala API for controlling tracing from Scala code
- **SafeZoneTracingExample.scala** - Example demonstrating tracing usage

## How to Enable Tracing

Tracing is disabled by default to avoid performance overhead in production builds. To enable it:

### Option 1: Modify ZoneTracing.h

Uncomment this line in `ZoneTracing.h`:
```c
#define ZONE_ENABLE_TRACING
```

### Option 2: Compiler Flag

Add the compile flag when building Scala Native:
```bash
export CFLAGS="-DZONE_ENABLE_TRACING"
```

Or in your `build.sbt`:
```scala
nativeConfig ~= { c =>
  c.withCompileOptions(c.compileOptions :+ "-DZONE_ENABLE_TRACING")
}
```

## What is Measured

The tracing system captures:

### Operation Counts
- Zone open/close operations
- Zone allocations
- Memory page claims and reclaims
- Chunk and page allocations

### Memory Statistics
- Total bytes allocated
- Peak memory usage
- Current memory in use
- Average bytes per allocation

### Timing Information (in microseconds)
- Zone open/close time
- Allocation time
- Pool claim/reclaim time
- Average time per operation

## Usage from Scala

```scala
import scala.scalanative.memory.{SafeZone, SafeZoneTracing}

// Initialize tracing (resets counters)
SafeZoneTracing.init()

// Perform zone allocations
SafeZone { sz =>
  given SafeZone = sz
  
  // Allocate objects
  val obj1 = sz.alloc(new MyClass())
  val obj2 = sz.alloc(new AnotherClass())
  
  // ... use objects ...
}

// Print performance statistics
SafeZoneTracing.printStats()
```

## Sample Output

```
========== SafeZone Performance Statistics ==========

--- Operation Counts ---
Zone open:                1000
Zone close:               1000
Zone alloc:               50000
Zone claim:               2500
Pool claim:               2500
Pool reclaim:             2500
Chunk allocations:        10
Page allocations:         2500

--- Memory Statistics ---
Total bytes allocated:    10485760 (10.00 MB)
Peak bytes allocated:     2097152 (2.00 MB)
Current bytes allocated:  0 (0.00 MB)

--- Timing Statistics (Total / Average per call) ---
Zone open:         50.234 ms /      50.234 μs
Zone close:        45.123 ms /      45.123 μs
Zone alloc:       125.456 ms /       2.509 μs
Pool claim:        80.234 ms /      32.094 μs
Pool reclaim:      75.123 ms /      30.049 μs
Avg bytes per alloc:      209 bytes
====================================================
```

## Performance Impact

When tracing is **disabled** (default):
- Zero overhead - all macros compile to no-ops
- No timing measurements
- No counter updates

When tracing is **enabled**:
- Minimal overhead from counter increments
- `clock_gettime()` calls for timing (~20-50ns per call on modern systems)
- Memory overhead: ~128 bytes for statistics structure

## Integration with Benchmarks

For more comprehensive benchmarking, combine with:

1. **JMH** (Java Microbenchmark Harness) for Scala-level benchmarks
2. **perf** on Linux: `perf stat -e cycles,instructions,cache-misses ./your-program`
3. **Instruments** on macOS for detailed profiling
4. **Valgrind Callgrind** for detailed call graphs

## Best Practices

1. **Production builds**: Keep tracing disabled
2. **Development/profiling**: Enable selectively when investigating performance
3. **Automated tests**: Call `SafeZoneTracing.printStats()` in cleanup to catch regressions
4. **Continuous integration**: Run periodic performance tests with tracing enabled

## Future Enhancements

Potential additions:
- Per-zone statistics (not just global)
- Histogram of allocation sizes
- JSON/CSV output format for automated analysis
- Integration with system profiling tools
- Thread-safe counters for concurrent use
