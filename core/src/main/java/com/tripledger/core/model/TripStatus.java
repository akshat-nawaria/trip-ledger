package com.tripledger.core.model;

public enum TripStatus {
    COMPUTED,  // every segment measured properly
    DEGRADED   // at least one gap used the x1.4 guess because routing was down - recompute later
}
