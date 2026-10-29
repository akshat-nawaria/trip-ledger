package com.tripledger.app.service;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

// the few checks bean validation can't express nicely. All of them fail with a clear 400.
public final class RequestChecks {

    // letters, digits, dash, underscore. Keeps ids safe to log and to put in URLs.
    private static final Pattern TRIP_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int MAX_KEY_LENGTH = 128;

    // no cab shift is longer than this; a bigger window is almost certainly a client bug, and it
    // would make the clock-skew filter useless
    public static final Duration MAX_TRIP_WINDOW = Duration.ofHours(24);

    private RequestChecks() {
    }

    public static void tripId(String tripId) {
        if (tripId == null || !TRIP_ID.matcher(tripId).matches()) {
            throw new InvalidRequestException("Trip id must be 1-64 characters: letters, digits, '-' or '_'");
        }
    }

    public static void idempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException("Idempotency-Key header must be 1-" + MAX_KEY_LENGTH + " characters");
        }
    }

    public static void tripWindow(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new InvalidRequestException("windowEnd must be after windowStart");
        }
        if (Duration.between(start, end).compareTo(MAX_TRIP_WINDOW) > 0) {
            throw new InvalidRequestException("A trip window can be at most " + MAX_TRIP_WINDOW.toHours() + " hours");
        }
    }
}
