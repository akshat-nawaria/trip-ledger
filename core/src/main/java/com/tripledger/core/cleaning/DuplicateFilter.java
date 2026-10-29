package com.tripledger.core.cleaning;

import com.tripledger.core.model.Ping;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps just one ping per timestamp.
 *
 * The phone can't be in two places at the same moment, so if two pings share a timestamp
 * one of them has to go. Usually it's a plain retry (identical ping sent twice on a bad network).
 * If the two actually disagree on the position, keep the more accurate one.
 *
 * The extra tie-breaks (seq, then coordinates) look like overkill, but they make sure the
 * same ping wins no matter what order things arrived in. Otherwise shuffled input could
 * give a different bill.
 */
public final class DuplicateFilter implements PingFilter {

    public static final String NAME = "duplicate";

    static final Comparator<Ping> BEST_FIRST = Comparator
            .comparingInt(Ping::accuracyMetres)
            .thenComparingLong(Ping::deviceSeq)
            .thenComparingLong(p -> p.point().latE6())
            .thenComparingLong(p -> p.point().lonE6());

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Ping> apply(List<Ping> pings) {
        Map<Instant, Ping> bestPerInstant = new LinkedHashMap<>();
        for (Ping ping : pings) {
            bestPerInstant.merge(ping.timestamp(), ping,
                    (kept, candidate) -> BEST_FIRST.compare(candidate, kept) < 0 ? candidate : kept);
        }
        return new ArrayList<>(bestPerInstant.values());
    }
}
