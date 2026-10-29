package com.tripledger.core.geo;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrack.ping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tripledger.core.model.Ping;
import org.junit.jupiter.api.Test;

class KinematicsTest {

    @Test
    void oneKilometreInOneMinuteIsSixtyKmph() {
        Ping a = ping(0, ORIGIN);
        Ping b = ping(60, north(ORIGIN, 1_000));

        assertThat(Kinematics.speedKmph(a, b)).isCloseTo(60.0, within(0.05));
    }

    @Test
    void argumentOrderDoesNotMatter() {
        Ping a = ping(0, ORIGIN);
        Ping b = ping(60, north(ORIGIN, 1_000));

        assertThat(Kinematics.speedKmph(b, a)).isEqualTo(Kinematics.speedKmph(a, b));
    }

    @Test
    void sameInstantSamePlaceIsStandingStill() {
        assertThat(Kinematics.speedKmph(ping(10, ORIGIN), ping(10, ORIGIN))).isEqualTo(0.0);
    }

    @Test
    void sameInstantDifferentPlaceIsInfinitelyFast() {
        double speed = Kinematics.speedKmph(ping(10, ORIGIN), ping(10, north(ORIGIN, 5)));

        assertThat(speed).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void plausibilityIsInclusiveOfTheLimit() {
        Ping a = ping(0, ORIGIN);
        Ping b = ping(60, north(ORIGIN, 1_000)); // about 60 km/h

        assertThat(Kinematics.isPlausible(a, b, 61.0)).isTrue();
        assertThat(Kinematics.isPlausible(a, b, 59.0)).isFalse();
    }
}
