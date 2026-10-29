package com.tripledger.app.routing;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.engine.RouteLookup;
import com.tripledger.core.model.GeoPoint;

/**
 * The RouteLookup the engine gets for ONE trip. Same answers as the gateway, but it also keeps
 * score: how many road distances this trip needed, how many came free from the cache, how many
 * cost a real call, and how many we couldn't get at all. That's the "calls per trip" number
 * that goes on the computation run later (S5).
 *
 * Not thread-safe, and it doesn't need to be. One trip is computed on one thread.
 */
public final class TripRouting implements RouteLookup {

    private final RoutingGateway gateway;
    private int requests;
    private int cacheHits;
    private int providerCalls;
    private int unavailable;

    TripRouting(RoutingGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public RouteAnswer route(GeoPoint from, GeoPoint to) {
        requests++;
        RoutingGateway.Outcome outcome = gateway.lookup(from, to);
        switch (outcome.source()) {
            case CACHE -> cacheHits++;
            case PROVIDER -> providerCalls++;
            case PROVIDER_FAILED -> {
                providerCalls++; // it failed, but the request still went out
                unavailable++;
            }
            case CIRCUIT_OPEN -> unavailable++;
        }
        return outcome.answer();
    }

    public RoutingUsage usage() {
        return new RoutingUsage(requests, cacheHits, providerCalls, unavailable);
    }
}
