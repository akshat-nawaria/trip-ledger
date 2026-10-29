package com.tripledger.core.cleaning;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.ping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripledger.core.model.Ping;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccuracyFilterTest {

    private final AccuracyFilter filter = new AccuracyFilter(50);

    @Test
    void keepsPingsAtOrBetterThanTheLimit() {
        Ping perfect = ping(0, ORIGIN, 0);
        Ping atLimit = ping(5, ORIGIN, 50);

        assertThat(filter.apply(List.of(perfect, atLimit))).containsExactly(perfect, atLimit);
    }

    @Test
    void dropsPingsWorseThanTheLimit() {
        Ping good = ping(0, ORIGIN, 10);
        Ping justOver = ping(5, ORIGIN, 51);
        Ping terrible = ping(10, ORIGIN, 300);

        assertThat(filter.apply(List.of(good, justOver, terrible))).containsExactly(good);
    }

    @Test
    void rejectsANonPositiveLimit() {
        assertThatThrownBy(() -> new AccuracyFilter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
