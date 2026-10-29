package com.tripledger.app.routing;

import static com.tripledger.app.routing.TestRouting.place;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.engine.RouteAnswer;
import com.tripledger.core.geo.Distance;
import com.tripledger.core.model.RouteEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinnedRoutesTest {

    private final MockRoutingProvider mock = new MockRoutingProvider(1.3);
    private final TripRouting live = TestRouting.gateway(mock, TestRouting.newBreaker()).newTrip();

    @Test
    void aGapWeAlreadyPaidForIsReplayedWithoutAskingAnyone() {
        PinnedRoutes routes = new PinnedRoutes(
                List.of(new RouteEvidence(place(0), place(3), Distance.ofMetres(4_321))), live);

        RouteAnswer answer = routes.route(place(0), place(3));

        assertThat(answer).isEqualTo(RouteAnswer.found(Distance.ofMetres(4_321)));
        assertThat(routes.replayed()).isEqualTo(1);
        assertThat(mock.calls()).isEqualTo(0);
        assertThat(live.usage()).isEqualTo(new RoutingUsage(0, 0, 0, 0));
    }

    @Test
    void aNewGapStillGoesToTheProvider() {
        PinnedRoutes routes = new PinnedRoutes(
                List.of(new RouteEvidence(place(0), place(3), Distance.ofMetres(4_321))), live);

        RouteAnswer answer = routes.route(place(3), place(6));

        assertThat(answer).isInstanceOf(RouteAnswer.Found.class);
        assertThat(routes.replayed()).isEqualTo(0);
        assertThat(mock.calls()).isEqualTo(1);
    }

    @Test
    void pinnedRoutesStillWorkWhileTheProviderIsDown() {
        mock.setDown(true);
        PinnedRoutes routes = new PinnedRoutes(
                List.of(new RouteEvidence(place(0), place(3), Distance.ofMetres(4_321))), live);

        assertThat(routes.route(place(0), place(3))).isInstanceOf(RouteAnswer.Found.class);
        assertThat(routes.route(place(3), place(6))).isInstanceOf(RouteAnswer.Unavailable.class);
    }

    @Test
    void whenAGapWasRoutedMoreThanOnceTheNewestAnswerWins() {
        PinnedRoutes routes = new PinnedRoutes(List.of(
                new RouteEvidence(place(0), place(3), Distance.ofMetres(5_000)),   // newest first
                new RouteEvidence(place(0), place(3), Distance.ofMetres(4_000))), live);

        assertThat(routes.route(place(0), place(3))).isEqualTo(RouteAnswer.found(Distance.ofMetres(5_000)));
    }

    @Test
    void directionMatters() {
        PinnedRoutes routes = new PinnedRoutes(
                List.of(new RouteEvidence(place(0), place(3), Distance.ofMetres(4_321))), live);

        routes.route(place(3), place(0));

        assertThat(routes.replayed()).isEqualTo(0);
    }
}
