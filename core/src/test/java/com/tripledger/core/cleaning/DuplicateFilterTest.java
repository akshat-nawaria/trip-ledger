package com.tripledger.core.cleaning;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.T0;
import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrack.ping;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.model.Ping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DuplicateFilterTest {

    private final DuplicateFilter filter = new DuplicateFilter();

    @Test
    void exactRepeatsCollapseToOne() {
        Ping original = ping(0, ORIGIN);
        Ping retry = ping(0, ORIGIN);
        Ping next = ping(5, north(ORIGIN, 50));

        assertThat(filter.apply(List.of(original, retry, next))).containsExactly(original, next);
    }

    @Test
    void sameInstantDifferentPositionKeepsTheMostAccurate() {
        Ping rough = ping(0, north(ORIGIN, 30), 40);
        Ping precise = ping(0, ORIGIN, 4);

        assertThat(filter.apply(List.of(rough, precise))).containsExactly(precise);
        assertThat(filter.apply(List.of(precise, rough))).containsExactly(precise);
    }

    @Test
    void equalAccuracyTieIsBrokenByDeviceSequence() {
        Ping first = new Ping(T0, ORIGIN, 5, 1);
        Ping second = new Ping(T0, north(ORIGIN, 10), 5, 2);

        assertThat(filter.apply(List.of(second, first))).containsExactly(first);
    }

    @Test
    void keptSetDoesNotDependOnArrivalOrder() {
        List<Ping> pings = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            pings.add(ping(i, north(ORIGIN, i * 10.0), 5 + (i % 7)));
            pings.add(ping(i, north(ORIGIN, i * 10.0 + 3), 3 + (i % 5)));
        }
        var expected = new HashSet<>(filter.apply(pings));

        Random random = new Random(7);
        for (int round = 0; round < 200; round++) {
            List<Ping> shuffled = new ArrayList<>(pings);
            Collections.shuffle(shuffled, random);
            assertThat(new HashSet<>(filter.apply(shuffled))).isEqualTo(expected);
        }
    }

    @Test
    void doesNotModifyItsInput() {
        List<Ping> input = new ArrayList<>(List.of(ping(0, ORIGIN), ping(0, ORIGIN)));

        filter.apply(input);

        assertThat(input).hasSize(2);
    }
}
