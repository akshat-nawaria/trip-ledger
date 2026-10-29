package com.tripledger.app.routing;

import static com.tripledger.app.routing.TestRouting.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

// breaker settings are the application.yml ones: last 10 calls, at least 5, open at 50%, 3 trial calls
class CircuitBreakingProviderTest {

    private final MockRoutingProvider mock = new MockRoutingProvider(1.3);
    private final CircuitBreaker breaker = TestRouting.newBreaker();
    private final CircuitBreakingProvider provider = new CircuitBreakingProvider(mock, breaker);

    private void callAndExpectFailure() {
        assertThatThrownBy(() -> provider.drivingDistance(place(0), place(3)))
                .isInstanceOf(RoutingException.class);
    }

    private void openTheCircuit() {
        mock.setDown(true);
        for (int i = 0; i < 5; i++) {
            callAndExpectFailure();
        }
    }

    @Test
    void healthyProviderAnswersGoStraightThrough() {
        assertThat(provider.drivingDistance(place(0), place(3)))
                .isEqualTo(mock.drivingDistance(place(0), place(3)));
        assertThat(provider.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void fiveFailuresOpenTheCircuitAndThenTheProviderIsLeftAlone() {
        openTheCircuit();
        assertThat(provider.state()).isEqualTo(CircuitBreaker.State.OPEN);

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> provider.drivingDistance(place(0), place(3)))
                    .isInstanceOf(RoutingCircuitOpenException.class);
        }
        assertThat(mock.calls()).isEqualTo(5); // the 20 extra requests never reached the provider
    }

    @Test
    void oneFailureAmongManySuccessesDoesNotOpenIt() {
        for (int i = 0; i < 9; i++) {
            provider.drivingDistance(place(0), place(3));
        }
        mock.setDown(true);
        callAndExpectFailure();

        assertThat(provider.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void trialCallsThatWorkCloseTheCircuitAgain() {
        openTheCircuit();
        mock.setDown(false);
        breaker.transitionToHalfOpenState(); // what the 30 s wait would do on its own

        for (int i = 0; i < 3; i++) {
            provider.drivingDistance(place(0), place(3));
        }

        assertThat(provider.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void trialCallsThatFailOpenItAgain() {
        openTheCircuit();
        breaker.transitionToHalfOpenState();

        for (int i = 0; i < 3; i++) {
            callAndExpectFailure();
        }

        assertThat(provider.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
