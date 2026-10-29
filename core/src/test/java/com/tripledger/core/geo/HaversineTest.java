package com.tripledger.core.geo;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.east;
import static com.tripledger.core.support.TestTrack.north;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tripledger.core.model.GeoPoint;
import org.junit.jupiter.api.Test;

class HaversineTest {

    @Test
    void samePointIsZeroMetres() {
        assertThat(Haversine.metresBetween(ORIGIN, ORIGIN)).isEqualTo(0.0);
    }

    @Test
    void oneDegreeOfLatitudeMatchesTheEarthRadius() {
        GeoPoint a = GeoPoint.ofDegrees(12.0, 77.0);
        GeoPoint b = GeoPoint.ofDegrees(13.0, 77.0);
        double expected = Haversine.EARTH_RADIUS_METRES * Math.PI / 180.0; // about 111,195 m

        assertThat(Haversine.metresBetween(a, b)).isCloseTo(expected, within(0.5));
    }

    @Test
    void measuresAKilometreNorthAndEast() {
        assertThat(Haversine.metresBetween(ORIGIN, north(ORIGIN, 1_000))).isCloseTo(1_000.0, within(0.2));
        assertThat(Haversine.metresBetween(ORIGIN, east(ORIGIN, 1_000))).isCloseTo(1_000.0, within(0.2));
    }

    @Test
    void isSymmetric() {
        GeoPoint other = east(north(ORIGIN, 3_456), 789);

        assertThat(Haversine.metresBetween(ORIGIN, other)).isEqualTo(Haversine.metresBetween(other, ORIGIN));
    }

    @Test
    void givesBitIdenticalResultsEveryTime() {
        GeoPoint other = east(north(ORIGIN, 12_345), 6_789);
        double first = Haversine.metresBetween(ORIGIN, other);

        for (int i = 0; i < 10_000; i++) {
            assertThat(Haversine.metresBetween(ORIGIN, other)).isEqualTo(first);
        }
    }

    @Test
    void handlesAntipodalPointsWithoutNaN() {
        double metres = Haversine.metresBetween(GeoPoint.ofDegrees(10.0, 20.0), GeoPoint.ofDegrees(-10.0, -160.0));

        assertThat(metres).isCloseTo(Math.PI * Haversine.EARTH_RADIUS_METRES, within(1.0));
    }

    @Test
    void betweenReturnsWholeMillimetres() {
        GeoPoint other = north(ORIGIN, 1_000);

        assertThat(Haversine.between(ORIGIN, other))
                .isEqualTo(Distance.ofMetres(Haversine.metresBetween(ORIGIN, other)));
    }
}
