package com.tripledger.core.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PingTest {

    private static final GeoPoint POINT = GeoPoint.ofDegrees(12.97, 77.75);

    @Test
    void rejectsNegativeAccuracy() {
        assertThatThrownBy(() -> new Ping(Instant.EPOCH, POINT, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingTimestamp() {
        assertThatThrownBy(() -> new Ping(null, POINT, 5, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingPoint() {
        assertThatThrownBy(() -> new Ping(Instant.EPOCH, null, 5, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
