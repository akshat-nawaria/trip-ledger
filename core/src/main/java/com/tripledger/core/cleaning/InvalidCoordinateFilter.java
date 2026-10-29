package com.tripledger.core.cleaning;

import com.tripledger.core.model.Ping;
import java.util.List;

// Throws away pings with impossible coordinates, or the classic (0, 0) "no fix" value.
public final class InvalidCoordinateFilter implements PingFilter {

    public static final String NAME = "invalid-coordinate";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Ping> apply(List<Ping> pings) {
        return pings.stream().filter(p -> p.point().isValid()).toList();
    }
}
