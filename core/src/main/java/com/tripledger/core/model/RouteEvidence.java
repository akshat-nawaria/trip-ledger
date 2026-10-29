package com.tripledger.core.model;

import com.tripledger.core.geo.Distance;

/**
 * A driving distance the routing provider gave us and that we actually used.
 * We keep these with the result so a recompute can replay them - if the map provider
 * changes its roads next month, an old bill still comes out exactly the same.
 */
public record RouteEvidence(GeoPoint from, GeoPoint to, Distance drivingDistance) {
}
