#ifndef ZONE_TRACING_H
#define ZONE_TRACING_H

#include <stdint.h>
#include <stdbool.h>

// Enable tracing by defining ZONE_ENABLE_TRACING at compile time
// or uncomment the line below:
#define ZONE_ENABLE_TRACING

#ifdef ZONE_ENABLE_TRACING

#include <time.h>
#include <stdio.h>

typedef struct {
    uint64_t zone_open_count;
    uint64_t zone_close_count;
    uint64_t zone_alloc_count;
    uint64_t zone_claim_count;
    
    uint64_t pool_claim_count;
    uint64_t pool_reclaim_count;
    uint64_t chunk_alloc_count;
    uint64_t page_alloc_count;
    
    uint64_t total_bytes_allocated;
    uint64_t peak_bytes_allocated;
    uint64_t current_bytes_allocated;
    
    // Timing measurements in nanoseconds
    uint64_t zone_open_time_ns;
    uint64_t zone_close_time_ns;
    uint64_t zone_alloc_time_ns;
    uint64_t pool_claim_time_ns;
    uint64_t pool_reclaim_time_ns;
} ZoneTracingStats;

extern ZoneTracingStats zone_tracing_stats;
extern bool zone_tracing_enabled;

// Initialize tracing
void zone_tracing_init();

// Print statistics
void zone_tracing_print_stats();

// Reset statistics
void zone_tracing_reset();

// Get current time in nanoseconds
static inline uint64_t zone_tracing_get_time_ns() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

// Timing helpers
#define ZONE_TRACE_START() uint64_t _trace_start = zone_tracing_get_time_ns()
#define ZONE_TRACE_END(counter) \
    do { \
        if (zone_tracing_enabled) { \
            uint64_t _trace_end = zone_tracing_get_time_ns(); \
            (counter) += (_trace_end - _trace_start); \
        } \
    } while(0)

// Counter macros
#define ZONE_TRACE_INC(counter) \
    do { \
        if (zone_tracing_enabled) { \
            (counter)++; \
        } \
    } while(0)

#define ZONE_TRACE_ADD(counter, value) \
    do { \
        if (zone_tracing_enabled) { \
            (counter) += (value); \
        } \
    } while(0)

#define ZONE_TRACE_UPDATE_PEAK(current, peak) \
    do { \
        if (zone_tracing_enabled && (current) > (peak)) { \
            (peak) = (current); \
        } \
    } while(0)

#else // !ZONE_ENABLE_TRACING

// No-op macros when tracing is disabled
#define zone_tracing_init()
#define zone_tracing_print_stats()
#define zone_tracing_reset()
#define ZONE_TRACE_START()
#define ZONE_TRACE_END(counter)
#define ZONE_TRACE_INC(counter)
#define ZONE_TRACE_ADD(counter, value)
#define ZONE_TRACE_UPDATE_PEAK(current, peak)

#endif // ZONE_ENABLE_TRACING

#endif // ZONE_TRACING_H
