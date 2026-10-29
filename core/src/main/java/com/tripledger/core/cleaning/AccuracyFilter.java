package com.tripledger.core.cleaning;

import com.tripledger.core.model.Ping;
import java.util.List;

// Drops pings where the device itself admits it's way off (accuracy worse than the limit).
public final class AccuracyFilter implements PingFilter {

    public static final String NAME = "low-accuracy";

    private final int maxAccuracyMetres;

    public AccuracyFilter(int maxAccuracyMetres) {
        if (maxAccuracyMetres <= 0) {
            throw new IllegalArgumentException("maxAccuracyMetres must be > 0");
        }
        this.maxAccuracyMetres = maxAccuracyMetres;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Ping> apply(List<Ping> pings) {
        return pings.stream().filter(p -> p.accuracyMetres() <= maxAccuracyMetres).toList();
    }
}
