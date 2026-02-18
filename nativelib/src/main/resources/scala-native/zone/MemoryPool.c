#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include "MemoryPool.h"
#include "../gc/shared/GCScalaNative.h"
#include "../gc/shared/MemoryMap.h"
#include "ZoneTracing.h"

MemoryPool *MemoryPool_open() {
    MemoryPool *pool = malloc(sizeof(MemoryPool));
    pool->chunkPageCount = MEMORYPOOL_MIN_CHUNK_COUNT;
    pool->chunk = NULL;
    pool->page = NULL;
    return pool;
}

void MemoryPool_alloc_chunk(MemoryPool *pool) {
    ZONE_TRACE_INC(zone_tracing_stats.chunk_alloc_count);
    
    MemoryChunk *chunk = malloc(sizeof(MemoryChunk));
    chunk->size = pool->chunkPageCount * MEMORYPOOL_PAGE_SIZE;
    chunk->offset = 0;
    chunk->start = memoryMapOrExitOnError(chunk->size);
    chunk->next = pool->chunk;
    pool->chunk = chunk;
    if (pool->chunkPageCount < MEMORYPOOL_MAX_CHUNK_COUNT) {
        pool->chunkPageCount *= 2;
    }
}

void MemoryPool_alloc_page(MemoryPool *pool) {
    ZONE_TRACE_INC(zone_tracing_stats.page_alloc_count);
    
    if (pool->chunk == NULL || pool->chunk->offset >= pool->chunk->size) {
        MemoryPool_alloc_chunk(pool);
    }
    MemoryPage *page = malloc(sizeof(MemoryPage));
    page->start = pool->chunk->start + pool->chunk->offset;
    page->offset = 0;
    page->size = MEMORYPOOL_PAGE_SIZE;
    page->next = pool->page;
    pool->chunk->offset += page->size;
    pool->page = page;
}

MemoryPage *MemoryPool_claim(MemoryPool *pool) {
    ZONE_TRACE_START();
    
    if (pool->page == NULL) {
        MemoryPool_alloc_page(pool);
    }
    MemoryPage *result = pool->page;
    pool->page = result->next;
    result->next = NULL;
    result->offset = 0;
    // Notify the GC that the page is in use.
    scalanative_add_roots(result->start, result->start + result->size);
    
    ZONE_TRACE_INC(zone_tracing_stats.pool_claim_count);
    ZONE_TRACE_END(zone_tracing_stats.pool_claim_time_ns);
    
    return result;
}

void MemoryPool_reclaim(MemoryPool *pool, MemoryPage *head) {
    ZONE_TRACE_START();
    
    // Notify the GC that the pages are no longer in use.
    MemoryPage *page = head, *tail = NULL;
    while (page != NULL) {
        scalanative_remove_roots(page->start, page->start + page->size);
        tail = page;
        page = page->next;
    }
    // Append the reclaimed pages to the pool.
    if (tail != NULL) {
        tail->next = pool->page;
        pool->page = head;
    }
    
    ZONE_TRACE_INC(zone_tracing_stats.pool_reclaim_count);
    ZONE_TRACE_END(zone_tracing_stats.pool_reclaim_time_ns);
}

void MemoryPool_close(MemoryPool *pool) {
    // Free chunks.
    MemoryChunk *chunk = pool->chunk, *preChunk = NULL;
    while (chunk != NULL) {
        preChunk = chunk;
        chunk = chunk->next;
        memoryUnmapOrExitOnError(preChunk->start, preChunk->size);
        free(preChunk);
    }
    // Free pages.
    MemoryPage *page = pool->page, *prePage = NULL;
    while (page != NULL) {
        prePage = page;
        page = page->next;
        free(prePage);
    }
    // Free the pool.
    free(pool);
}
