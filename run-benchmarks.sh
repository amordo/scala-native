#!/bin/bash

# Safe Region Benchmark Runner
# Automatically runs benchmarks with different page sizes
# as described in "Safe Region for Scala Native" paper

set -e

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export SCALANATIVE_MODE=release-fast
export GC_INITIAL_HEAP_SIZE=1M

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
NUM_RUNS=20 # hardcoded in BenchPerformance.scala
RESULTS_DIR="/tmp/benchmark-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Page sizes to test (in bytes)
PAGE_SIZES=(4096 8192 65536 4294967296)  # 2^12, 2^13, 2^16, 2^32
PAGE_SIZE_LABELS=("2^12 (4KB)" "2^13 (8KB)" "2^16 (64KB)" "2^32 (4GB)")

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Safe Region Performance Benchmark Suite${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Timestamp: $TIMESTAMP"
echo "Number of runs per benchmark: $NUM_RUNS"
echo "Results directory: $RESULTS_DIR"
echo ""

# Create results directory
rm -rf "$RESULTS_DIR"
mkdir -p "$RESULTS_DIR"

# # Change to scala-native directory for sbt
# cd scala-native

# Path to MemoryPool.h
MEMORYPOOL_HEADER="nativelib/src/main/resources/scala-native/zone/MemoryPool.h"

# Function to run a benchmark and save results
run_benchmark() {
    local name=$1
    local main_number=$2
    local page_size=$3
    local page_label=$4
    local output_file=$5
    
    echo -e "${GREEN}Running: $name${NC}"
    
    # Modify page size in MemoryPool.h if specified
    if [ -n "$page_size" ]; then
        echo "Setting page size: $page_label ($page_size bytes)"
        sed -i.bak "s/#define MEMORYPOOL_PAGE_SIZE [0-9]*/#define MEMORYPOOL_PAGE_SIZE $page_size/" "$MEMORYPOOL_HEADER"
        echo "Modified $MEMORYPOOL_HEADER"
    fi
    
    echo "$main_number" | sbt "sandbox3_next/run" 2>&1 | tee "../$output_file"
    
    # # Restore original if backup exists
    # if [ -f "$MEMORYPOOL_HEADER.bak" ]; then
    #     mv "$MEMORYPOOL_HEADER.bak" "$MEMORYPOOL_HEADER"
    # fi
    
    echo -e "${YELLOW}Results saved to: $output_file${NC}"
    echo ""
}

# 1. GC-Only Baseline (Standard)
echo -e "\n${BLUE}[1/10] GC-Only Baseline (Standard Operations)${NC}"
echo "------------------------------------------------------------"
run_benchmark \
    "GC-Only Standard" \
    "5" \
    "" \
    "" \
    "$RESULTS_DIR/gc_only_standard_${TIMESTAMP}.log"

# # 2. SafeZone with different page sizes (Standard)
# for i in ${!PAGE_SIZES[@]}; do
#     page_size=${PAGE_SIZES[$i]}
#     page_label=${PAGE_SIZE_LABELS[$i]}
#     idx=$((i + 2))
    
#     echo -e "\n${BLUE}[$idx/10] SafeZone Standard (Page Size: $page_label)${NC}"
#     echo "------------------------------------------------------------"
#     run_benchmark \
#         "SafeZone Standard $page_label" \
#         "7" \
#         "$page_size" \
#         "$page_label" \
#         "$RESULTS_DIR/safezone_standard_${page_size}_${TIMESTAMP}.log"
# done

# # 6. GC-Only with Tree-Walking
# echo -e "\n${BLUE}[6/10] GC-Only (Extra Tree-Walking)${NC}"
# echo "------------------------------------------------------------"
# run_benchmark \
#     "GC-Only Tree-Walking" \
#     "6" \
#     "" \
#     "" \
#     "$RESULTS_DIR/gc_only_treewalking_${TIMESTAMP}.log"

# # 7-10. SafeZone with Tree-Walking (test with optimal page size and a couple others)
# OPTIMAL_PAGE_SIZE=8192  # 2^13, the best from paper
# OPTIMAL_LABEL="2^13 (8KB)"

# echo -e "\n${BLUE}[7/10] SafeZone Tree-Walking (Page Size: $OPTIMAL_LABEL - Optimal)${NC}"
# echo "------------------------------------------------------------"
# run_benchmark \
#     "SafeZone Tree-Walking $OPTIMAL_LABEL" \
#     "8" \
#     "$OPTIMAL_PAGE_SIZE" \
#     "$OPTIMAL_LABEL" \
#     "$RESULTS_DIR/safezone_treewalking_${OPTIMAL_PAGE_SIZE}_${TIMESTAMP}.log"

# # Additional configurations for comparison
# echo -e "\n${BLUE}[8/10] SafeZone Tree-Walking (Page Size: 2^12)${NC}"
# echo "------------------------------------------------------------"
# run_benchmark \
#     "SafeZone Tree-Walking 2^12" \
#     "8" \
#     "4096" \
#     "2^12 (4KB)" \
#     "$RESULTS_DIR/safezone_treewalking_4096_${TIMESTAMP}.log"

# echo -e "\n${BLUE}[9/10] SafeZone Tree-Walking (Page Size: 2^16)${NC}"
# echo "------------------------------------------------------------"
# run_benchmark \
#     "SafeZone Tree-Walking 2^16" \
#     "8" \
#     "65536" \
#     "2^16 (64KB)" \
#     "$RESULTS_DIR/safezone_treewalking_65536_${TIMESTAMP}.log"

# # Return to workspace root
# cd ..

# Generate summary
echo -e "\n${BLUE}========================================${NC}"
echo -e "${BLUE}Generating Summary Report${NC}"
echo -e "${BLUE}========================================${NC}"

SUMMARY_FILE="$RESULTS_DIR/summary_${TIMESTAMP}.txt"

{
    echo "Safe Region Benchmark Results Summary"
    echo "====================================="
    echo ""
    echo "Timestamp: $TIMESTAMP"
    echo "Number of runs: $NUM_RUNS"
    echo ""
    echo "Results:"
    echo "--------"
    echo ""
    
    # Extract average times from log files
    for logfile in "$RESULTS_DIR"/*_${TIMESTAMP}.log; do
        if [ -f "$logfile" ]; then
            benchmark_name=$(basename "$logfile" .log)
            avg_time=$(grep "Average:" "$logfile" | tail -1 | awk '{print $2, $3}')
            if [ -n "$avg_time" ]; then
                echo "$benchmark_name: $avg_time"
            fi
        fi
    done
    
    echo ""
    echo "Expected Results (from paper, Intel Core i7 2.3GHz, 16GB RAM):"
    echo "--------------------------------------------------------------"
    echo "GC-Only Standard: ~944 ms"
    echo "SafeZone Standard (2^13): ~589.3 ms (37.6% improvement)"
    echo "GC-Only Tree-Walking: ~1056.4 ms"
    echo "SafeZone Tree-Walking (2^13): ~645.8 ms (38.9% improvement)"
    echo ""
    echo "All results saved in: $RESULTS_DIR/"
    
} | tee "$SUMMARY_FILE"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Benchmark Suite Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Summary saved to: ${YELLOW}$SUMMARY_FILE${NC}"
echo -e "Full logs in: ${YELLOW}$RESULTS_DIR/${NC}"
echo ""
echo "To analyze results:"
echo "  cat $SUMMARY_FILE"
echo "  grep 'Average:' $RESULTS_DIR/*_${TIMESTAMP}.log"
