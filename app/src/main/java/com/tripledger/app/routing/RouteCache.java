package com.tripledger.app.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tripledger.core.geo.Distance;
import java.time.Duration;
import java.util.function.Function;

/**
 * In-memory cache of road distances (Caffeine), keyed by snapped grid cells.
 *
 * Only real answers go in. If the provider fails, nothing is stored, so the next trip tries
 * again instead of being stuck with a failure until the entry expires.
 *
 * get() is atomic per key: if ten trips ask for the same new route at the same moment, one of
 * them calls the provider and the other nine just wait for that answer. No duplicate paid calls.
 */
public final class RouteCache {

    private final Cache<RouteKey, Distance> cache;

    public RouteCache(long maxEntries, Duration timeToLive) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("cache size must be positive but was " + maxEntries);
        }
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(timeToLive)
                .recordStats()
                .build();
    }

    // gives back the distance plus whether it was already in the cache or had to be loaded just now
    public Result get(RouteKey key, Function<RouteKey, Distance> loader) {
        boolean[] loadedNow = {false};
        Distance distance = cache.get(key, k -> {
            loadedNow[0] = true;
            return loader.apply(k);
        });
        return new Result(distance, !loadedNow[0]);
    }

    public long size() {
        return cache.estimatedSize();
    }

    public double hitRate() {
        return cache.stats().hitRate();
    }

    public record Result(Distance distance, boolean fromCache) {
    }
}
