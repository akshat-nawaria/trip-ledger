package com.tripledger.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeoPointTest {

    @Test
    void storesDegreesAsMicroDegrees() {
        GeoPoint point = GeoPoint.ofDegrees(12.9698, 77.75);

        assertThat(point.latE6()).isEqualTo(12_969_800L);
        assertThat(point.lonE6()).isEqualTo(77_750_000L);
    }

    @Test
    void roundsToTheNearestMicroDegree() {
        assertThat(GeoPoint.ofDegrees(12.0000004, 77.0).latE6()).isEqualTo(12_000_000L);
        assertThat(GeoPoint.ofDegrees(12.0000006, 77.0).latE6()).isEqualTo(12_000_001L);
    }

    @Test
    void sameDegreesGiveEqualPoints() {
        assertThat(GeoPoint.ofDegrees(12.9698, 77.75)).isEqualTo(GeoPoint.ofDegrees(12.9698, 77.75));
    }

    @Test
    void normalPointIsValid() {
        assertThat(GeoPoint.ofDegrees(12.9698, 77.75).isValid()).isTrue();
    }

    @Test
    void nullIslandIsInvalid() {
        assertThat(GeoPoint.ofDegrees(0.0, 0.0).isValid()).isFalse();
    }

    @Test
    void outOfRangeLatitudeIsInvalid() {
        assertThat(GeoPoint.ofDegrees(90.000001, 10.0).isValid()).isFalse();
        assertThat(GeoPoint.ofDegrees(-91.0, 10.0).isValid()).isFalse();
    }

    @Test
    void outOfRangeLongitudeIsInvalid() {
        assertThat(GeoPoint.ofDegrees(10.0, 180.000001).isValid()).isFalse();
        assertThat(GeoPoint.ofDegrees(10.0, -181.0).isValid()).isFalse();
    }

    @Test
    void boundaryValuesAreValid() {
        assertThat(GeoPoint.ofDegrees(90.0, 180.0).isValid()).isTrue();
        assertThat(GeoPoint.ofDegrees(-90.0, -180.0).isValid()).isTrue();
    }

    @Test
    void notANumberBecomesInvalid() {
        assertThat(GeoPoint.ofDegrees(Double.NaN, Double.NaN).isValid()).isFalse();
    }
}
