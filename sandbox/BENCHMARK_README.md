# Safe Region Performance Benchmarks

This directory contains performance benchmarks for evaluating Safe Regions (SafeZone) in Scala Native, as described in the paper **"Safe Region for Scala Native"** by Yawen Guan.

## Benchmark Overview

The benchmark suite implements Hans Boehm's GCBench benchmark that evaluates garbage collection performance using binary tree workloads. It compares:

1. **GC-Only (Baseline)**: Standard garbage collection without SafeZone
2. **SafeZone**: Region-based memory management with capture checking
3. **Tree-Walking**: Extra operations that walk the tree structure

## Files

- `BenchPerformance.scala` - Complete benchmark suite
- `BenchOrig.scala` - Original GC-only benchmark
- `BenchZones.scala` - SafeZone benchmark example

## Running the Benchmarks

### Run All Benchmarks

```bash
sbt "sandbox/run TestAllBenchmarks"
```

This runs all 4 benchmark configurations with 20 iterations each (as in the paper).

### Run Individual Benchmarks

```bash
# GC-Only with standard operations
sbt "sandbox/run TestGCOnly"

# GC-Only with extra tree-walking
sbt "sandbox/run TestGCTreeWalking"

# SafeZone with standard operations
sbt "sandbox/run TestSafeZoneStandard"

# SafeZone with extra tree-walking
sbt "sandbox/run TestSafeZoneTreeWalking"
```

## Configuring Page Sizes

The paper tests SafeZone with different page sizes:
- 2^12 bytes (4 KB)
- 2^13 bytes (8 KB)
- 2^16 bytes (64 KB)
- 2^32 bytes (4 GB)

The SafeZone allocator reads the page size from the `SAFEZONE_PAGE_SIZE` environment variable at runtime. If not set, it defaults to 8192 bytes (8 KB).

### Configuration Methods

**Method 1: Environment Variable (Recommended for Benchmarking)**

Set the environment variable before running:

```bash
export SAFEZONE_PAGE_SIZE=8192  # 2^13 bytes
sbt "sandbox/run TestSafeZoneStandard"
```

**Method 2: Inline with Command**

```bash
SAFEZONE_PAGE_SIZE=4096 sbt "sandbox/run TestSafeZoneStandard"
```

The runtime will print the configured page size on startup:
```
[SafeZone] Using page size from environment: 8192 bytes
```

## Expected Results

Based on the paper's experimental results (2.3 GHz quad-core Intel Core i7, 16 GB RAM):

| Memory Strategy | Page Size | Operations | Avg. Time | Improvement |
|----------------|-----------|------------|-----------|-------------|
| Only GC | -- | Standard | 944 ms | -- |
| Safe Regions | 2^12 B | Standard | 673.8 ms | 28.6% |
| Safe Regions | 2^13 B | Standard | 589.3 ms | **37.6%** |
| Safe Regions | 2^16 B | Standard | 629.2 ms | 33.4% |
| Safe Regions | 2^32 B | Standard | 651.6 ms | 31.0% |
| Only GC | -- | Extra tree-walking | 1056.4 ms | -- |
| Safe Regions | 2^13 B | Extra tree-walking | 645.8 ms | **38.9%** |

**Key Findings:**
- SafeZone provides up to 37.6% improvement in standard operations
- SafeZone provides up to 38.9% improvement with extra tree-walking
- Optimal page size: 2^13 bytes (8 KB)

## Benchmark Details

### Workload Characteristics

- **Stretch Tree Depth**: 18 (creates ~16 MB of data)
- **Long-Lived Tree Depth**: 16 (creates ~4 MB of data)
- **Array Size**: 500,000 elements (~4 MB)
- **Tree Depth Range**: 4 to 16 (with step of 2)

### What the Benchmark Tests

1. **Memory Allocation**: Creates many short-lived and some long-lived objects
2. **GC Pressure**: Rapidly allocates and deallocates tree structures
3. **Pointer Chasing**: Tree traversal and population operations
4. **Array Access**: Long-lived array with scattered access patterns

### Tree-Walking Extension

The tree-walking variant adds extra traversal operations that:
- Increase pointer-chasing operations
- Add more stress to the memory system
- Better simulate real-world workloads with complex data structure access

## Interpreting Results

When comparing results:

1. **Look at Average Time**: Primary metric for comparison
2. **Calculate Improvement**: `(GC_Time - SafeZone_Time) / GC_Time * 100%`
3. **Check Consistency**: Min/Max spread indicates variance
4. **Compare Page Sizes**: Find optimal configuration for your hardware

### Example Calculation

```
GC Average: 944 ms
SafeZone Average: 589.3 ms
Improvement: (944 - 589.3) / 944 * 100% = 37.6%
```

## System Requirements

- **Scala Native**: 0.5.x or later with capture checking support
- **Scala Version**: 3.3.x (with experimental.captureChecking)
- **Native Dependencies**: GC library (Boehm GC or Immix GC)
- **Recommended Hardware**: Multi-core processor, 8+ GB RAM

## Troubleshooting

### SafeZoneTracing Not Found

If you get errors about `SafeZoneTracing`, ensure your Scala Native build includes the SafeZone implementation:

```scala
// In nativelib, ensure SafeZone.scala and SafeZoneTracing.scala are included
```

### Compilation Errors with Capture Checking

Enable capture checking in your Scala compiler options:

```scala
scalacOptions ++= Seq(
  "-language:experimental.captureChecking"
)
```

### Performance Varies Significantly

Ensure:
- Run benchmarks when system is idle
- Disable frequency scaling: `sudo cpupower frequency-set --governor performance`
- Close other applications
- Run multiple iterations (20+) for statistical significance

## References

1. Yawen Guan. "Safe Region for Scala Native". Semester Project at LAMP, supervised by Prof. Martin Odersky.
2. Hans Boehm and Mark Weiser. "Garbage collection in an uncooperative environment". Software: Practice and Experience, 1988.
3. Mads Tofte et al. "A retrospective on region-based memory management". 2004.

## Contributing

To add new benchmark variants:

1. Create a new object extending the benchmark pattern
2. Add a `@main` entry point
3. Use `BenchmarkRunner.runBenchmark()` for consistency
4. Document the benchmark purpose and expected results
