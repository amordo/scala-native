#!/bin/bash

# Script to build and run SafeZone with performance tracing enabled
# Usage: ./build-with-tracing.sh

set -e

echo "Building Scala Native with SafeZone performance tracing enabled..."
echo "=================================================================="

# Export the compile flag to enable tracing
export CFLAGS="${CFLAGS} -DZONE_ENABLE_TRACING"

# Optional: Add optimization flags for more realistic performance measurement
# export CFLAGS="${CFLAGS} -O2"

echo "CFLAGS: $CFLAGS"
echo ""

# Build the project
echo "Building with sbt..."
cd "$(dirname "$0")"

# If you want to run the tracing example:
sbt "nativelib/test:runMain scala.SafeZoneTracingExample"

# Or just compile:
# sbt nativelib/compile

echo ""
echo "Build completed with tracing enabled!"
echo ""
echo "To permanently enable tracing, modify:"
echo "  nativelib/src/main/resources/scala-native/zone/ZoneTracing.h"
echo "  and uncomment: #define ZONE_ENABLE_TRACING"
