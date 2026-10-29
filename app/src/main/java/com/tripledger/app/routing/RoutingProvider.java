package com.tripledger.app.routing;

import com.tripledger.core.geo.Distance;
import com.tripledger.core.model.GeoPoint;

/**
 * Anything that can tell us the driving distance between two points. A real map API
 * (OSRM, Google...) in production, MockRoutingProvider for local runs and tests.
 *
 * The deal for every implementation: return a real distance or throw RoutingException.
 * Never return null and never invent a number when something breaks. Deciding what to do
 * on failure is the gateway's job, not the provider's.
 */
public interface RoutingProvider {

    String name();

    Distance drivingDistance(GeoPoint from, GeoPoint to);
}
