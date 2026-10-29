package com.tripledger.app.routing;

import static com.tripledger.app.routing.TestRouting.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripledger.core.geo.Distance;
import com.tripledger.core.geo.Haversine;
import org.junit.jupiter.api.Test;

class MockRoutingProviderTest {

    private final MockRoutingProvider provider = new MockRoutingProvider(1.3);

    @Test
    void roadIsTheStraightLineTimesTheRoadFactor() {
        Distance expected = Distance.ofMetres(Haversine.metresBetween(place(0), place(3)) * 1.3);

        assertThat(provider.drivingDistance(place(0), place(3))).isEqualTo(expected);
    }

    @Test
    void sameQuestionAlwaysGetsTheSameAnswer() {
        Distance first = provider.drivingDistance(place(0), place(3));

        assertThat(provider.drivingDistance(place(0), place(3))).isEqualTo(first);
        assertThat(provider.calls()).isEqualTo(2);
    }

    @Test
    void switchedOffItFailsButStillCountsTheCall() {
        provider.setDown(true);

        assertThatThrownBy(() -> provider.drivingDistance(place(0), place(3)))
                .isInstanceOf(RoutingException.class);
        assertThat(provider.calls()).isEqualTo(1);
    }

    @Test
    void worksAgainOnceSwitchedBackOn() {
        provider.setDown(true);
        provider.setDown(false);

        assertThat(provider.drivingDistance(place(0), place(3)).millimetres() > 0).isTrue();
    }

    @Test
    void rejectsARoadShorterThanTheStraightLine() {
        assertThatThrownBy(() -> new MockRoutingProvider(0.9)).isInstanceOf(IllegalArgumentException.class);
    }
}
