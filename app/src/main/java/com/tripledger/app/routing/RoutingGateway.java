package com.tripledger.app.routing;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.model.GeoPoint;

/**
 * The app's side of the core RouteLookup port: what the distance engine ends up talking to
 * whenever it needs a road distance.
 *
 *   snap both ends to the grid -> cache -> circuit breaker -> provider
 *
 * The cache sits in front of the breaker on purpose. When the provider is down we can still answer
 * every route we've already seen, and only brand-new gaps fall back to the straight line.
 *
 * It never throws at the engine. Whatever goes wrong turns into RouteAnswer.unavailable(...),
 * and rule 3 in the engine (RoutingUnavailableRule) takes it from there.
 */
public final class RoutingGateway {

    private final RoutingProvider provider;
    private final RouteCache cache;
    private final long cellMicroDegrees;

    public RoutingGateway(RoutingProvider provider, RouteCache cache, long cellMicroDegrees) {
        if (cellMicroDegrees <= 0) {
            throw new IllegalArgumentException("cell size must be positive but was " + cellMicroDegrees);
        }
        this.provider = provider;
        this.cache = cache;
        this.cellMicroDegrees = cellMicroDegrees;
    }

    // hand one of these to the engine for each trip, then read usage() to see what the trip cost
    public TripRouting newTrip() {
        return new TripRouting(this);
    }

    Outcome lookup(GeoPoint from, GeoPoint to) {
        RouteKey key = RouteKey.snap(from, to, cellMicroDegrees);
        try {
            RouteCache.Result result = cache.get(key, k -> provider.drivingDistance(k.from(), k.to()));
            RouteSource source = result.fromCache() ? RouteSource.CACHE : RouteSource.PROVIDER;
            return new Outcome(RouteAnswer.found(result.distance()), source);
        } catch (RoutingCircuitOpenException e) {
            return new Outcome(RouteAnswer.unavailable(e.getMessage()), RouteSource.CIRCUIT_OPEN);
        } catch (RoutingException e) {
            return new Outcome(RouteAnswer.unavailable(e.getMessage()), RouteSource.PROVIDER_FAILED);
        }
    }

    record Outcome(RouteAnswer answer, RouteSource source) {
    }
}
