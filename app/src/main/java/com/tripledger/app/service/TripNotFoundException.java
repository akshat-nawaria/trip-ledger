package com.tripledger.app.service;

// -> 404. Also what a caller gets for a trip that belongs to another tenant: we never say it exists.
public class TripNotFoundException extends RuntimeException {

    public TripNotFoundException(String tripId) {
        super("No trip '" + tripId + "' found");
    }
}
