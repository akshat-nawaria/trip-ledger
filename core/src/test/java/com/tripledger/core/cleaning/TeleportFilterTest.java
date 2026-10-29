package com.tripledger.core.cleaning;

import static com.tripledger.core.support.TestTrack.ORIGIN;
import static com.tripledger.core.support.TestTrack.east;
import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrack.pathMetres;
import static com.tripledger.core.support.TestTrack.ping;
import static com.tripledger.core.support.TestTrack.straightNorth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Ping;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeleportFilterTest {

    private static final double MAX_SPEED_KMPH = 120.0;

    private final TeleportFilter filter = new TeleportFilter(MAX_SPEED_KMPH, 3);

    @Test
    void cleanTrackIsUnchanged() {
        List<Ping> track = straightNorth(20, 5, 50); // 36 km/h

        assertThat(filter.apply(track)).containsExactlyElementsOf(track);
    }

    @Test
    void caseStudyBurstOfNineKmInFourSecondsIsDroppedAndDistanceIsUnchanged() {
        List<Ping> track = straightNorth(20, 5, 50);
        GeoPoint anchor = track.get(8).point(); // the fix at t = 40 s
        Ping burst1 = ping(42, east(anchor, 9_000));
        Ping burst2 = ping(44, east(anchor, 9_050));

        List<Ping> withBurst = new ArrayList<>(track.subList(0, 9));
        withBurst.add(burst1);
        withBurst.add(burst2);
        withBurst.addAll(track.subList(9, track.size()));

        List<Ping> cleaned = filter.apply(withBurst);

        assertThat(cleaned).containsExactlyElementsOf(track);
        assertThat(pathMetres(cleaned)).isEqualTo(pathMetres(track));
    }

    @Test
    void speedJustUnderTheLimitIsKept() {
        Ping a = ping(0, ORIGIN);
        Ping b = ping(10, north(ORIGIN, 330)); // 118.8 km/h

        assertThat(filter.apply(List.of(a, b))).containsExactly(a, b);
    }

    @Test
    void speedJustOverTheLimitIsDropped() {
        Ping a = ping(0, ORIGIN);
        Ping b = ping(10, north(ORIGIN, 340)); // 122.4 km/h
        Ping c = ping(20, north(ORIGIN, 400)); // 72 km/h from a

        assertThat(filter.apply(List.of(a, b, c))).containsExactly(a, c);
    }

    @Test
    void wrongFirstFixIsReplacedOnceGoodPingsAgree() {
        Ping badFirst = ping(0, east(ORIGIN, 50_000));
        List<Ping> good = new ArrayList<>();
        GeoPoint position = ORIGIN;
        for (int t = 5; t <= 25; t += 5) {
            good.add(ping(t, position));
            position = north(position, 50);
        }
        List<Ping> input = new ArrayList<>();
        input.add(badFirst);
        input.addAll(good);

        assertThat(filter.apply(input)).containsExactlyElementsOf(good);
    }

    @Test
    void gpsStuckFarAwayForAFewPingsDoesNotOverruleTheRealTrack() {
        List<Ping> before = straightNorth(5, 5, 50); // t = 0..20
        GeoPoint last = before.get(4).point();
        GeoPoint farAway = east(last, 9_000);
        List<Ping> stuck = List.of(ping(21, farAway), ping(22, farAway), ping(23, farAway));
        List<Ping> after = new ArrayList<>();
        GeoPoint position = north(last, 50);
        for (int t = 25; t <= 40; t += 5) {
            after.add(ping(t, position));
            position = north(position, 50);
        }

        List<Ping> input = new ArrayList<>(before);
        input.addAll(stuck);
        input.addAll(after);
        List<Ping> expected = new ArrayList<>(before);
        expected.addAll(after);

        assertThat(filter.apply(input)).containsExactlyElementsOf(expected);
    }

    @Test
    void scatteredNoiseThatDisagreesWithItselfIsDropped() {
        List<Ping> before = straightNorth(5, 5, 50);
        GeoPoint last = before.get(4).point();
        List<Ping> noise = List.of(
                ping(21, east(last, 9_000)),
                ping(22, east(last, -9_000)),
                ping(23, north(last, 9_000)),
                ping(24, east(last, 9_000)));
        Ping resumed = ping(25, north(last, 50));

        List<Ping> input = new ArrayList<>(before);
        input.addAll(noise);
        input.add(resumed);
        List<Ping> expected = new ArrayList<>(before);
        expected.add(resumed);

        assertThat(filter.apply(input)).containsExactlyElementsOf(expected);
    }

    @Test
    void emptyAndSinglePingInputsArePassedThrough() {
        Ping only = ping(0, ORIGIN);

        assertThat(filter.apply(List.of())).isEmpty();
        assertThat(filter.apply(List.of(only))).containsExactly(only);
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new TeleportFilter(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeleportFilter(120, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
