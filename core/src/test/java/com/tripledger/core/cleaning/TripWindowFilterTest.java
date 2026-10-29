package com.tripledger.core.cleaning;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.T0;
import static com.tripledger.core.support.TestTrack.ping;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.model.Ping;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripWindowFilterTest {

    // window is T0 .. T0 + 1h, with 10 minutes of slack either side
    private final TripWindowFilter filter =
            new TripWindowFilter(T0, T0.plusSeconds(3600), Duration.ofMinutes(10));

    @Test
    void keepsPingsInsideTheWindowAndTheSlack() {
        Ping start = ping(0, ORIGIN);
        Ping slightlyEarly = ping(-600, ORIGIN);   // exactly 10 min before
        Ping slightlyLate = ping(4200, ORIGIN);    // exactly 10 min after

        assertThat(filter.apply(List.of(slightlyEarly, start, slightlyLate)))
                .containsExactly(slightlyEarly, start, slightlyLate);
    }

    @Test
    void dropsPingsFromAPhoneWithAWrongClock() {
        Ping good = ping(100, ORIGIN);
        Ping yesterday = ping(-86_400, ORIGIN);
        Ping tooLate = ping(4201, ORIGIN);

        assertThat(filter.apply(List.of(yesterday, good, tooLate))).containsExactly(good);
    }
}
