package com.tripledger.app.routing;

import static com.tripledger.app.routing.TestRouting.place;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.model.GeoPoint;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

class RoutingGatewayTest {

    private final MockRoutingProvider mock = new MockRoutingProvider(1.3);
    private final CircuitBreaker breaker = TestRouting.newBreaker();
    private final RoutingGateway gateway = TestRouting.gateway(mock, breaker);

    // two points ~15 m from place(0) and place(3), still inside the same grid cells
    private final GeoPoint nearStart = new GeoPoint(place(0).latE6() + 120, place(0).lonE6() + 100);
    private final GeoPoint nearEnd = new GeoPoint(place(3).latE6() + 80, place(3).lonE6() + 130);

    @Test
    void firstAskGoesToTheProviderSecondComesFromTheCache() {
        TripRouting trip = gateway.newTrip();

        RouteAnswer first = trip.route(place(0), place(3));
        RouteAnswer second = trip.route(place(0), place(3));

        assertThat(first).isInstanceOf(RouteAnswer.Found.class);
        assertThat(second).isEqualTo(first);
        assertThat(trip.usage()).isEqualTo(new RoutingUsage(2, 1, 1, 0));
        assertThat(mock.calls()).isEqualTo(1);
    }

    @Test
    void nearbyPointsInTheSameCellsShareOneRoute() {
        RouteAnswer original = gateway.newTrip().route(place(0), place(3));
        RouteAnswer nearby = gateway.newTrip().route(nearStart, nearEnd);

        assertThat(nearby).isEqualTo(original);
        assertThat(mock.calls()).isEqualTo(1);
    }

    @Test
    void theAnswerDoesNotDependOnWhichTripAskedFirst() {
        // two fresh setups, same two questions asked in opposite order
        RoutingGateway one = TestRouting.gateway(new MockRoutingProvider(1.3), TestRouting.newBreaker());
        RoutingGateway two = TestRouting.gateway(new MockRoutingProvider(1.3), TestRouting.newBreaker());

        RouteAnswer oneExact = one.newTrip().route(place(0), place(3));
        RouteAnswer oneNearby = one.newTrip().route(nearStart, nearEnd);
        RouteAnswer twoNearby = two.newTrip().route(nearStart, nearEnd);
        RouteAnswer twoExact = two.newTrip().route(place(0), place(3));

        assertThat(oneExact).isEqualTo(twoExact);
        assertThat(oneNearby).isEqualTo(twoNearby);
        assertThat(oneExact).isEqualTo(oneNearby);
    }

    @Test
    void providerDownBecomesUnavailableNotAnException() {
        mock.setDown(true);
        TripRouting trip = gateway.newTrip();

        RouteAnswer answer = trip.route(place(0), place(3));

        assertThat(answer).isInstanceOf(RouteAnswer.Unavailable.class);
        assertThat(trip.usage()).isEqualTo(new RoutingUsage(1, 0, 1, 1));
    }

    @Test
    void failuresAreNotCachedSoTheNextTripTriesAgain() {
        mock.setDown(true);
        RouteAnswer duringOutage = gateway.newTrip().route(place(0), place(3));
        mock.setDown(false);
        RouteAnswer afterOutage = gateway.newTrip().route(place(0), place(3));

        assertThat(duringOutage).isInstanceOf(RouteAnswer.Unavailable.class);
        assertThat(afterOutage).isInstanceOf(RouteAnswer.Found.class);
        assertThat(mock.calls()).isEqualTo(2);
    }

    @Test
    void whileTheCircuitIsOpenKnownRoutesStillComeFromTheCache() {
        RouteAnswer known = gateway.newTrip().route(place(0), place(3)); // cached while healthy

        mock.setDown(true);
        TripRouting outage = gateway.newTrip();
        for (int i = 10; i < 15; i++) {
            outage.route(place(i), place(i + 1)); // 5 brand-new routes while the provider is down
        }
        // the breaker counts that earlier success too: after 1 ok + 4 failed it has its 5 calls at 80%
        // failure, so it opens there and the 5th new route never reaches the provider at all
        assertThat(outage.usage()).isEqualTo(new RoutingUsage(5, 0, 4, 5));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        TripRouting later = gateway.newTrip();
        RouteAnswer stillKnown = later.route(place(0), place(3));
        RouteAnswer unknown = later.route(place(20), place(21));

        assertThat(stillKnown).isEqualTo(known);
        assertThat(unknown).isInstanceOf(RouteAnswer.Unavailable.class);
        assertThat(later.usage()).isEqualTo(new RoutingUsage(2, 1, 0, 1)); // nothing paid for
        assertThat(mock.calls()).isEqualTo(5); // 1 healthy + 4 failed, the open circuit cost nothing
    }

    @Test
    void eachTripKeepsItsOwnScore() {
        TripRouting first = gateway.newTrip();
        first.route(place(0), place(3));
        first.route(place(3), place(6));

        TripRouting second = gateway.newTrip();
        second.route(place(0), place(3));

        assertThat(first.usage()).isEqualTo(new RoutingUsage(2, 0, 2, 0));
        assertThat(second.usage()).isEqualTo(new RoutingUsage(1, 1, 0, 0));
    }
}
