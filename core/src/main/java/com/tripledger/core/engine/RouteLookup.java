package com.tripledger.core.engine;

import com.tripledger.core.model.GeoPoint;

/**
 * The engine's only way to get a road distance.
 *
 * Defined here in core, implemented in the app module (cache, circuit breaker, real provider...).
 * That keeps core free of HTTP and lets tests plug in a fake that never touches the network.
 */
@FunctionalInterface
public interface RouteLookup {

    RouteAnswer route(GeoPoint from, GeoPoint to);
}
