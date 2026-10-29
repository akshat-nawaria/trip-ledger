package com.tripledger.core.engine;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.TripEvidence;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * A SHA-256 fingerprint of everything that decides a trip's distance: the evidence, the billing
 * rules and the rule-set version.
 *
 * The engine is deterministic, so same fingerprint = same answer. That means if a trip gets
 * computed again and nothing changed (say a device re-sent pings we already had), we can skip
 * the whole computation - and the paid routing calls that come with it.
 *
 * Pings, milestones and stops are sorted before hashing, so upload order doesn't matter.
 * The same evidence sent in a different order gives the same fingerprint.
 */
public final class EvidenceFingerprint {

    private EvidenceFingerprint() {
    }

    public static String of(TripEvidence trip, BillingRules rules, String ruleSetVersion) {
        List<String> lines = new ArrayList<>();
        lines.add("trip|" + trip.tripId() + "|" + trip.tripType() + "|" + trip.windowStart() + "|"
                + trip.windowEnd() + "|" + point(trip.plannedOffice()));
        // BillingRules is a record, so toString lists every threshold in a fixed order
        lines.add("rules|" + ruleSetVersion + "|" + rules);

        List<String> evidence = new ArrayList<>();
        trip.pings().forEach(p -> evidence.add("ping|" + p.timestamp() + "|" + point(p.point()) + "|"
                + p.accuracyMetres() + "|" + p.deviceSeq()));
        trip.milestones().forEach(m -> evidence.add("milestone|" + m.type() + "|" + m.employeeRef() + "|"
                + point(m.point()) + "|" + m.time()));
        trip.plannedStops().forEach(s -> evidence.add("stop|" + s.riderType() + "|" + s.employeeRef() + "|"
                + point(s.point()) + "|" + s.time()));
        evidence.sort(null); // plain string order - any fixed order works, it just has to be the same every time
        lines.addAll(evidence);

        return sha256(String.join("\n", lines));
    }

    private static String point(GeoPoint p) {
        return p == null ? "-" : p.latE6() + "," + p.lonE6();
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // every JVM is required to ship SHA-256, so this really can't happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
