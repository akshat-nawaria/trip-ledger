package com.tripledger.core.cleaning;

import com.tripledger.core.model.Ping;
import java.util.Comparator;
import java.util.List;

/**
 * Puts pings in time order (seq number as tie-break).
 *
 * Pings show up out of order all the time - retries, late batch uploads, etc.
 * Everything after this step assumes they're sorted, so we sort once here and
 * stop worrying about arrival order.
 */
public final class TimeOrderFilter implements PingFilter {

    public static final String NAME = "time-order";

    static final Comparator<Ping> BY_TIME = Comparator
            .comparing(Ping::timestamp)
            .thenComparingLong(Ping::deviceSeq);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Ping> apply(List<Ping> pings) {
        return pings.stream().sorted(BY_TIME).toList();
    }
}
