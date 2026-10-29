package com.tripledger.core.cleaning;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.T0;
import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrack.ping;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.model.Ping;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeOrderFilterTest {

    private final TimeOrderFilter filter = new TimeOrderFilter();

    @Test
    void sortsOutOfOrderPingsByTime() {
        Ping first = ping(0, ORIGIN);
        Ping second = ping(5, north(ORIGIN, 50));
        Ping third = ping(10, north(ORIGIN, 100));

        assertThat(filter.apply(List.of(third, first, second))).containsExactly(first, second, third);
    }

    @Test
    void equalTimestampsAreOrderedByDeviceSequence() {
        Ping seqTwo = new Ping(T0, ORIGIN, 5, 2);
        Ping seqOne = new Ping(T0, north(ORIGIN, 5), 5, 1);

        assertThat(filter.apply(List.of(seqTwo, seqOne))).containsExactly(seqOne, seqTwo);
    }

    @Test
    void alreadySortedInputIsUnchanged() {
        List<Ping> sorted = List.of(ping(0, ORIGIN), ping(5, ORIGIN), ping(10, ORIGIN));

        assertThat(filter.apply(sorted)).containsExactlyElementsOf(sorted);
    }
}
