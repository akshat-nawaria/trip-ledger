package com.tripledger.app.routing;

// where an answer for one road-distance question came from. Used to keep the per-trip score.
public enum RouteSource {
    CACHE,           // free, we'd seen this route before
    PROVIDER,        // paid call that worked
    PROVIDER_FAILED, // paid call that didn't work
    CIRCUIT_OPEN     // breaker was open, no call made at all
}
