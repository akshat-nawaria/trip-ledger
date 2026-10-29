package com.tripledger.app.service;

import com.tripledger.core.model.Anchor;
import com.tripledger.core.model.TripDistance;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The "why" that gets saved next to every run (as JSON) and shown in the breakdown:
 * what cleaning threw out, and which anchor points were used and where they came from.
 *
 * Kept as its own small shape instead of dumping core objects straight to JSON, so a refactor
 * inside core can't silently change what old runs look like.
 */
public final class RunExplanation {

    private RunExplanation() {
    }

    public record Cleaning(int received, int kept, Map<String, Integer> removed) {
    }

    public record AnchorUsed(String kind, String employeeRef, String source, double lat, double lon, Instant time) {

        static AnchorUsed of(Anchor a) {
            return new AnchorUsed(a.kind().name(), a.employeeRef(), a.source().name(),
                    a.point().latitude(), a.point().longitude(), a.time());
        }
    }

    public static Cleaning cleaningOf(TripDistance result) {
        return new Cleaning(result.cleaning().received(), result.cleaning().kept(), result.cleaning().removedByFilter());
    }

    public static List<AnchorUsed> anchorsOf(TripDistance result) {
        return result.anchors().stream().map(AnchorUsed::of).toList();
    }
}
