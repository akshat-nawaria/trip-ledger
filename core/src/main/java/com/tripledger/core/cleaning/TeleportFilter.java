package com.tripledger.core.cleaning;

import com.tripledger.core.geo.Kinematics;
import com.tripledger.core.model.Ping;
import java.util.ArrayList;
import java.util.List;

/**
 * Gets rid of "teleport" pings - points the cab could only reach by going faster than
 * the speed limit in the rules (120 km/h by default). Expects pings already sorted by time.
 *
 * How it works:
 * Compare every ping with the last one we accepted. Too fast to be real? Don't accept it,
 * park it in a "suspicious" list instead. This alone handles the PDF example (9 km in 4 s),
 * and the total distance doesn't change.
 *
 * The tricky case:
 * What if the ping we accepted is the bad one? Happens a lot with the very first fix of a
 * trip - the GPS hasn't settled yet. Then every good ping after it looks "impossible" and we'd
 * throw away the whole trip. So: if suspicious pings keep agreeing with each other, and we get
 * reanchorRun (3) of them in a row, we look back for the last accepted ping they connect to.
 * Whatever accepted pings come after that are probably the junk. If there are fewer of them
 * than suspicious ones, swap them out. If not, the suspicious ones stay rejected.
 *
 * That last check matters - without it, the GPS freezing on a wrong spot for 3 pings
 * would be enough to wipe out a perfectly good track.
 */
public final class TeleportFilter implements PingFilter {

    public static final String NAME = "teleport";

    private final double maxSpeedKmph;
    private final int reanchorRun;

    public TeleportFilter(double maxSpeedKmph, int reanchorRun) {
        if (maxSpeedKmph <= 0) {
            throw new IllegalArgumentException("maxSpeedKmph must be > 0");
        }
        if (reanchorRun < 2) {
            throw new IllegalArgumentException("reanchorRun must be >= 2");
        }
        this.maxSpeedKmph = maxSpeedKmph;
        this.reanchorRun = reanchorRun;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Ping> apply(List<Ping> pings) {
        List<Ping> accepted = new ArrayList<>(pings.size());
        List<Ping> suspicious = new ArrayList<>();

        for (Ping ping : pings) {
            if (accepted.isEmpty() || plausible(last(accepted), ping)) {
                accepted.add(ping);
                suspicious.clear();
                continue;
            }
            addToSuspiciousRun(suspicious, ping);
            if (suspicious.size() >= reanchorRun) {
                tryReanchor(accepted, suspicious);
            }
        }
        return List.copyOf(accepted);
    }

    // the suspicious list should always be a chain of pings that agree with each other.
    // if the new one doesn't fit the chain, start a fresh chain from it.
    private void addToSuspiciousRun(List<Ping> suspicious, Ping ping) {
        if (!suspicious.isEmpty() && !plausible(last(suspicious), ping)) {
            suspicious.clear();
        }
        suspicious.add(ping);
    }

    private void tryReanchor(List<Ping> accepted, List<Ping> suspicious) {
        Ping runStart = suspicious.get(0);
        int connectIndex = -1;
        for (int i = accepted.size() - 1; i >= 0; i--) {
            if (plausible(accepted.get(i), runStart)) {
                connectIndex = i;
                break;
            }
        }
        // how many accepted pings sit between "where the run connects" and now
        int outliers = accepted.size() - 1 - connectIndex;
        if (outliers < suspicious.size()) {
            accepted.subList(connectIndex + 1, accepted.size()).clear();
            accepted.addAll(suspicious);
            suspicious.clear();
        }
    }

    private boolean plausible(Ping from, Ping to) {
        return Kinematics.isPlausible(from, to, maxSpeedKmph);
    }

    private static Ping last(List<Ping> list) {
        return list.get(list.size() - 1);
    }
}
