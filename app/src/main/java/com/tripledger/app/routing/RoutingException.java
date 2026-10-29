package com.tripledger.app.routing;

// "couldn't get a road distance right now" - provider error, timeout, bad response, open circuit...
// a real HTTP provider should wrap its client errors in this, so the gateway only has one thing to catch
public class RoutingException extends RuntimeException {

    public RoutingException(String message) {
        super(message);
    }

    public RoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
