package com.tripledger.app.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripledger.core.model.GeoPoint;
import org.junit.jupiter.api.Test;

class RouteKeyTest {

    // cell [12_970_000, 12_970_200) x [77_750_000, 77_750_200), centre (12_970_100, 77_750_100)
    private final GeoPoint a = new GeoPoint(12_970_010, 77_750_020);
    private final GeoPoint aNearby = new GeoPoint(12_970_190, 77_750_180); // ~25 m away, same cell
    private final GeoPoint aNextCell = new GeoPoint(12_970_210, 77_750_020); // just over the edge
    private final GeoPoint b = new GeoPoint(12_990_000, 77_760_000);

    @Test
    void pointsInTheSameCellGetTheSameKey() {
        assertThat(RouteKey.snap(a, b, 200)).isEqualTo(RouteKey.snap(aNearby, b, 200));
    }

    @Test
    void aPointInTheNextCellGetsADifferentKey() {
        assertThat(RouteKey.snap(a, b, 200).equals(RouteKey.snap(aNextCell, b, 200))).isFalse();
    }

    @Test
    void directionMatters() {
        assertThat(RouteKey.snap(a, b, 200).equals(RouteKey.snap(b, a, 200))).isFalse();
    }

    @Test
    void theProviderIsAskedAboutTheCellCentreNotTheRawPoint() {
        RouteKey key = RouteKey.snap(a, b, 200);

        assertThat(key.from()).isEqualTo(new GeoPoint(12_970_100, 77_750_100));
        assertThat(key.to()).isEqualTo(new GeoPoint(12_990_100, 77_760_100));
    }

    @Test
    void negativeCoordinatesSnapDownwardsNotTowardsZero() {
        // -50 and +50 are 100 micro-degrees apart but sit in different cells: [-200, 0) and [0, 200)
        RouteKey south = RouteKey.snap(new GeoPoint(-50, -50), b, 200);
        RouteKey north = RouteKey.snap(new GeoPoint(50, 50), b, 200);

        assertThat(south.from()).isEqualTo(new GeoPoint(-100, -100));
        assertThat(north.from()).isEqualTo(new GeoPoint(100, 100));
    }

    @Test
    void rejectsACellSizeOfZero() {
        assertThatThrownBy(() -> RouteKey.snap(a, b, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
