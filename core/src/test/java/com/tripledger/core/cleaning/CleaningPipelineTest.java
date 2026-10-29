package com.tripledger.core.cleaning;

import static com.tripledger.core.support.TestTrack.east;
import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrack.ping;
import static com.tripledger.core.support.TestTrack.straightNorth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Ping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CleaningPipelineTest {

    private final CleaningPipeline pipeline = CleaningPipeline.standard(BillingRules.defaults());

    // 30 clean pings, 5 s apart, 50 m apart -> a steady 36 km/h
    private final List<Ping> track = straightNorth(30, 5, 50);

    // same track, but with all the usual real-world mess thrown in
    private List<Ping> messyTrack() {
        List<Ping> messy = new ArrayList<>(track);
        messy.add(track.get(3));                                          // duplicate (retry)
        messy.add(track.get(10));                                         // duplicate (retry)
        messy.add(ping(7, GeoPoint.ofDegrees(0, 0)));                     // invalid coordinate
        messy.add(ping(12, north(track.get(2).point(), 10), 80));         // low accuracy
        GeoPoint anchor = track.get(8).point();
        messy.add(ping(41, east(anchor, 9_000)));                         // teleport burst
        messy.add(ping(43, east(anchor, 9_040)));                         // teleport burst
        return messy;
    }

    @Test
    void runsFiltersInTheDocumentedOrder() {
        assertThat(pipeline.filterNames()).containsExactly(
                InvalidCoordinateFilter.NAME,
                DuplicateFilter.NAME,
                TimeOrderFilter.NAME,
                AccuracyFilter.NAME,
                TeleportFilter.NAME);
    }

    @Test
    void messyInputCleansBackToTheTrueTrack() {
        List<Ping> messy = messyTrack();
        Collections.shuffle(messy, new Random(1));

        CleaningResult result = pipeline.clean(messy);

        assertThat(result.pings()).containsExactlyElementsOf(track);
    }

    @Test
    void reportCountsWhatEachFilterRemoved() {
        CleaningReport report = pipeline.clean(messyTrack()).report();

        assertThat(report.received()).isEqualTo(36);
        assertThat(report.removedBy(InvalidCoordinateFilter.NAME)).isEqualTo(1);
        assertThat(report.removedBy(DuplicateFilter.NAME)).isEqualTo(2);
        assertThat(report.removedBy(TimeOrderFilter.NAME)).isEqualTo(0);
        assertThat(report.removedBy(AccuracyFilter.NAME)).isEqualTo(1);
        assertThat(report.removedBy(TeleportFilter.NAME)).isEqualTo(2);
        assertThat(report.kept()).isEqualTo(30);
        assertThat(report.totalRemoved()).isEqualTo(6);
    }

    @Test
    void arrivalOrderNeverChangesTheResult() {
        List<Ping> messy = messyTrack();
        CleaningResult expected = pipeline.clean(messy);

        Random random = new Random(42);
        for (int round = 0; round < 1_000; round++) {
            Collections.shuffle(messy, random);
            assertThat(pipeline.clean(messy)).isEqualTo(expected);
        }
    }

    @Test
    void emptyInputGivesAnEmptyResult() {
        CleaningResult result = pipeline.clean(List.of());

        assertThat(result.pings()).isEmpty();
        assertThat(result.report().received()).isEqualTo(0);
        assertThat(result.report().kept()).isEqualTo(0);
    }

    @Test
    void doesNotModifyTheCallersList() {
        List<Ping> messy = messyTrack();
        List<Ping> copy = new ArrayList<>(messy);

        pipeline.clean(messy);

        assertThat(messy).containsExactlyElementsOf(copy);
    }

    @Test
    void stricterAccuracyLimitRemovesMorePings() {
        BillingRules strict = BillingRules.builder().maxAccuracyMetres(4).build();

        CleaningResult result = CleaningPipeline.standard(strict).clean(track); // track accuracy is 5 m

        assertThat(result.pings()).isEmpty();
        assertThat(result.report().removedBy(AccuracyFilter.NAME)).isEqualTo(30);
    }

    @Test
    void rejectsAPipelineWithNoFilters() {
        assertThatThrownBy(() -> new CleaningPipeline(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
