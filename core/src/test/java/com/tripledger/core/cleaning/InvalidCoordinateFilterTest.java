package com.tripledger.core.cleaning;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.ping;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Ping;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidCoordinateFilterTest {

    private final InvalidCoordinateFilter filter = new InvalidCoordinateFilter();

    @Test
    void keepsValidPings() {
        List<Ping> pings = List.of(ping(0, ORIGIN), ping(5, ORIGIN));

        assertThat(filter.apply(pings)).containsExactlyElementsOf(pings);
    }

    @Test
    void dropsNullIslandAndOutOfRangePoints() {
        Ping good = ping(0, ORIGIN);
        Ping nullIsland = ping(5, GeoPoint.ofDegrees(0, 0));
        Ping badLatitude = ping(10, GeoPoint.ofDegrees(95, 77));

        assertThat(filter.apply(List.of(good, nullIsland, badLatitude))).containsExactly(good);
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertThat(filter.apply(List.of())).isEmpty();
    }
}
