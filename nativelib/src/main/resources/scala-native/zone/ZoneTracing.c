#define __SCALANATIVE_MEMORY_SAFEZONE
#if defined(SCALANATIVE_COMPILE_ALWAYS) || \
    defined(__SCALANATIVE_MEMORY_SAFEZONE)

#include "ZoneTracing.h"

#ifdef ZONE_ENABLE_TRACING

#include <stdio.h>
#include <string.h>

ZoneTracingStats zone_tracing_stats;
bool zone_tracing_enabled = true;

void zone_tracing_init() {
    memset(&zone_tracing_stats, 0, sizeof(ZoneTracingStats));
    zone_tracing_enabled = true;
    fprintf(stderr, "[SafeZone] Performance tracing initialized\n");
}

void zone_tracing_print_stats() {
    if (!zone_tracing_enabled) {
        return;
    }
    
    fprintf(stderr, "\n========== SafeZone Performance Statistics ==========\n");
    
    // Operation counts
    fprintf(stderr, "\n--- Operation Counts ---\n");
    fprintf(stderr, "Zone open:                %llu\n", 
            (unsigned long long)zone_tracing_stats.zone_open_count);
    fprintf(stderr, "Zone close:               %llu\n", 
            (unsigned long long)zone_tracing_stats.zone_close_count);
    fprintf(stderr, "Zone alloc:               %llu\n", 
            (unsigned long long)zone_tracing_stats.zone_alloc_count);
    fprintf(stderr, "Zone claim:               %llu\n", 
            (unsigned long long)zone_tracing_stats.zone_claim_count);
    fprintf(stderr, "Pool claim:               %llu\n", 
            (unsigned long long)zone_tracing_stats.pool_claim_count);
    fprintf(stderr, "Pool reclaim:             %llu\n", 
            (unsigned long long)zone_tracing_stats.pool_reclaim_count);
    fprintf(stderr, "Chunk allocations:        %llu\n", 
            (unsigned long long)zone_tracing_stats.chunk_alloc_count);
    fprintf(stderr, "Page allocations:         %llu\n", 
            (unsigned long long)zone_tracing_stats.page_alloc_count);
    
    // Memory statistics
    fprintf(stderr, "\n--- Memory Statistics ---\n");
    fprintf(stderr, "Total bytes allocated:    %llu (%.2f MB)\n",
            (unsigned long long)zone_tracing_stats.total_bytes_allocated,
            zone_tracing_stats.total_bytes_allocated / (1024.0 * 1024.0));
    fprintf(stderr, "Peak bytes allocated:     %llu (%.2f MB)\n",
            (unsigned long long)zone_tracing_stats.peak_bytes_allocated,
            zone_tracing_stats.peak_bytes_allocated / (1024.0 * 1024.0));
    fprintf(stderr, "Current bytes allocated:  %llu (%.2f MB)\n",
            (unsigned long long)zone_tracing_stats.current_bytes_allocated,
            zone_tracing_stats.current_bytes_allocated / (1024.0 * 1024.0));
    
    // Timing statistics
    fprintf(stderr, "\n--- Timing Statistics (Total / Average per call) ---\n");
    
    if (zone_tracing_stats.zone_open_count > 0) {
        fprintf(stderr, "Zone open:    %10.3f ms / %10.3f μs\n",
                zone_tracing_stats.zone_open_time_ns / 1000000.0,
                zone_tracing_stats.zone_open_time_ns / 
                    (zone_tracing_stats.zone_open_count * 1000.0));
    }
    
    if (zone_tracing_stats.zone_close_count > 0) {
        fprintf(stderr, "Zone close:   %10.3f ms / %10.3f μs\n",
                zone_tracing_stats.zone_close_time_ns / 1000000.0,
                zone_tracing_stats.zone_close_time_ns / 
                    (zone_tracing_stats.zone_close_count * 1000.0));
    }
    
    if (zone_tracing_stats.zone_alloc_count > 0) {
        fprintf(stderr, "Zone alloc:   %10.3f ms / %10.3f μs\n",
                zone_tracing_stats.zone_alloc_time_ns / 1000000.0,
                zone_tracing_stats.zone_alloc_time_ns / 
                    (zone_tracing_stats.zone_alloc_count * 1000.0));
        fprintf(stderr, "Avg bytes per alloc:      %llu bytes\n",
                zone_tracing_stats.total_bytes_allocated / 
                    zone_tracing_stats.zone_alloc_count);
    }
    
    if (zone_tracing_stats.pool_claim_count > 0) {
        fprintf(stderr, "Pool claim:   %10.3f ms / %10.3f μs\n",
                zone_tracing_stats.pool_claim_time_ns / 1000000.0,
                zone_tracing_stats.pool_claim_time_ns / 
                    (zone_tracing_stats.pool_claim_count * 1000.0));
    }
    
    if (zone_tracing_stats.pool_reclaim_count > 0) {
        fprintf(stderr, "Pool reclaim: %10.3f ms / %10.3f μs\n",
                zone_tracing_stats.pool_reclaim_time_ns / 1000000.0,
                zone_tracing_stats.pool_reclaim_time_ns / 
                    (zone_tracing_stats.pool_reclaim_count * 1000.0));
    }
    
    fprintf(stderr, "====================================================\n\n");
}

void zone_tracing_reset() {
    memset(&zone_tracing_stats, 0, sizeof(ZoneTracingStats));
}

#endif // ZONE_ENABLE_TRACING
#endif // SCALANATIVE_COMPILE_ALWAYS
