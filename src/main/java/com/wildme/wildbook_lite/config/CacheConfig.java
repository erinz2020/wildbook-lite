package com.wildme.wildbook_lite.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caffeine cache. In-memory, per-JVM. NOT a distributed cache.
 *
 * Caches:
 *  - "encounter"     : Encounter findById results, TTL 5min, max 1000 entries.
 *
 * Interview points:
 *
 *  - Cache-Aside (Look-Aside): the @Cacheable annotation. App reads cache,
 *    on miss reads DB, writes back. Cache is *additive* and not strongly
 *    consistent with the DB.
 *
 *  - Three classic problems:
 *      * Penetration (穿透)  : query for a key that never exists, every
 *                              request hits DB. Fix: cache "null" sentinel,
 *                              bloom filter, or block via rate limiting.
 *      * Breakdown  (击穿)   : one *hot* key expires, thundering herd. Fix:
 *                              mutex / refresh-ahead.
 *      * Avalanche  (雪崩)   : many keys expire together → DB overload. Fix:
 *                              jitter TTL, use refreshAfterWrite.
 *
 *  - Caffeine vs Redis: Caffeine is in-process (zero RPC), capped by JVM
 *    heap, and dies with the pod. Redis is shared across pods, survives,
 *    but costs a network hop + GC pressure on Redis side.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        Caffeine<Object, Object> spec = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1000);

        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(
            new CaffeineCache("encounter", spec.build())
        ));
        return mgr;
    }
}
