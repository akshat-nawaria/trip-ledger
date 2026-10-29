package com.tripledger.app.routing;

import static com.tripledger.app.routing.TestRouting.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
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
    void theWaitEndsByItselfEvenWhenNobodyCalls() throws InterruptedException {
        // same settings, just a 200 ms wait instead of 30 s so the test doesn't crawl
        CircuitBreaker quick = CircuitBreakingProvider.newBreaker("quick",
                new RoutingProperties.BreakerSettings(10, 5, 50f, Duration.ofMillis(200), 3));
        CircuitBreakingProvider quickProvider = new CircuitBreakingProvider(mock, quick);
        mock.setDown(true);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> quickProvider.drivingDistance(place(0), place(3)))
                    .isInstanceOf(RoutingException.class);
        }
        assertThat(quick.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // no calls at all from here on - before S9 it would have stayed OPEN forever like this
        long deadline = System.currentTimeMillis() + 3_000;
        while (quick.getState() == CircuitBreaker.State.OPEN && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }

        assertThat(quick.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(mock.calls()).isEqualTo(5);
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
