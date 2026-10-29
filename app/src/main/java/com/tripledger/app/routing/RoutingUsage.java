package com.tripledger.app.routing;

/**
 * What one trip cost us in routing.
 *
 * requests      - road distances the engine asked for (short gaps and parked time never ask)
 * cacheHits     - answered from the cache, free
 * providerCalls - real calls to the map service (including ones that failed)
 * unavailable   - couldn't get an answer, so that piece was paid as straight line x 1.4
 */
public record RoutingUsage(int requests, int cacheHits, int providerCalls, int unavailable) {
}
