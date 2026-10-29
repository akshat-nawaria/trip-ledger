package com.tripledger.app.persistence;

import com.tripledger.core.model.TripStatus;

// where a trip is in its life. Core only knows COMPUTED / DEGRADED; RECEIVED is the app's
// "new evidence arrived, the km on file are out of date" state.
public enum TripState {
    RECEIVED,
    COMPUTED,
    DEGRADED;

    public static TripState from(TripStatus status) {
        return switch (status) {
            case COMPUTED -> COMPUTED;
            case DEGRADED -> DEGRADED;
        };
    }
}
