#if defined(SCALANATIVE_COMPILE_ALWAYS) ||                                     \
    defined(__SCALANATIVE_MEMORY_SAFEZONE)
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <memory.h>
#include "Zone.h"
#include "Util.h"
#include "MemoryPool.h"
#include "ZoneTracing.h"

MemoryPool *scalanative_zone_default_pool = NULL;
LargeMemoryPool *scalanative_zone_default_largepool = NULL;

void *scalanative_zone_open() {
    ZONE_TRACE_START();
    
    if (scalanative_zone_default_pool == NULL) {
        scalanative_zone_default_pool = MemoryPool_open();
    }
    if (scalanative_zone_default_largepool == NULL) {
        scalanative_zone_default_largepool = LargeMemoryPool_open();
    }
    Zone *zone = malloc(sizeof(Zone));
    zone->pool = scalanative_zone_default_pool;
    zone->page = NULL;
    zone->largePool = scalanative_zone_default_largepool;
    zone->largePage = NULL;
    
    ZONE_TRACE_INC(zone_tracing_stats.zone_open_count);
    ZONE_TRACE_END(zone_tracing_stats.zone_open_time_ns);
    
    return (void *)zone;
}

void scalanative_zone_close(void *_zone) {
    ZONE_TRACE_START();
    
    Zone *zone = (Zone *)_zone;
    // Reclaim borrowed pages to the memory pool.
    MemoryPool_reclaim(zone->pool, zone->page);
    LargeMemoryPool_reclaim(zone->largePool, zone->largePage);
    free(zone);
    
    ZONE_TRACE_INC(zone_tracing_stats.zone_close_count);
    ZONE_TRACE_END(zone_tracing_stats.zone_close_time_ns);
}

MemoryPage *scalanative_zone_claim(Zone *zone, size_t size) {
    ZONE_TRACE_INC(zone_tracing_stats.zone_claim_count);
    return (size <= MEMORYPOOL_PAGE_SIZE)
               ? MemoryPool_claim(zone->pool)
               : LargeMemoryPool_claim(zone->largePool, Util_pad(size, 8));
}

void *scalanative_zone_alloc(void *_zone, void *info, size_t size) {
    ZONE_TRACE_START();
    
    Zone *zone = (Zone *)_zone;
    MemoryPage *page =
        (size <= MEMORYPOOL_PAGE_SIZE) ? zone->page : zone->largePage;
    page = (page == NULL) ? scalanative_zone_claim(zone, size) : page;
    size_t paddedOffset = Util_pad(page->offset, 8);
    size_t resOffset = 0;
    if (paddedOffset + size <= page->size) {
        resOffset = paddedOffset;
    } else {
        MemoryPage *newPage = scalanative_zone_claim(zone, size);
        newPage->next = page;
        page = newPage;
        resOffset = 0;
    }
    page->offset = resOffset + size;
    void *current = (void *)(page->start + resOffset);
    memset(current, 0, size);
    void **alloc = (void **)current;
    *alloc = info;
    if (size <= MEMORYPOOL_PAGE_SIZE) {
        zone->page = page;
    } else {
        zone->largePage = page;
    }
    
    // Update tracing statistics
    ZONE_TRACE_INC(zone_tracing_stats.zone_alloc_count);
    ZONE_TRACE_ADD(zone_tracing_stats.total_bytes_allocated, size);
    ZONE_TRACE_ADD(zone_tracing_stats.current_bytes_allocated, size);
    ZONE_TRACE_UPDATE_PEAK(zone_tracing_stats.current_bytes_allocated, 
                           zone_tracing_stats.peak_bytes_allocated);
    ZONE_TRACE_END(zone_tracing_stats.zone_alloc_time_ns);
    
    return (void *)alloc;
}
#endif