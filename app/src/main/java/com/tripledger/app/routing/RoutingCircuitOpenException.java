package com.tripledger.app.routing;

// the breaker is open, so we didn't even try the provider. Kept separate from a real failure
// because it didn't cost us a call - the per-trip numbers need to know the difference.
public class RoutingCircuitOpenException extends RoutingException {

    public RoutingCircuitOpenException(String message) {
        super(message);
    }
}
