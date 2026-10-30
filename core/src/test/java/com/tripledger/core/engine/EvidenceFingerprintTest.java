package com.tripledger.core.engine;

import static com.tripledger.core.support.TestTrack.north;
import static com.tripledger.core.support.TestTrack.ping;
import static com.tripledger.core.support.TestTrack.straightNorth;
import static com.tripledger.core.support.TestTrips.OFFICE;
import static com.tripledger.core.support.TestTrips.milestone;
import static com.tripledger.core.support.TestTrips.planned;
import static com.tripledger.core.support.TestTrips.trip;
import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.config.BillingRules;
import com.tripledger.core.model.GeoPoint;
import com.tripledger.core.model.Milestone;
import com.tripledger.core.model.MilestoneType;
import com.tripledger.core.model.Ping;
import com.tripledger.core.model.PlannedStop;
import com.tripledger.core.model.TripEvidence;
import com.tripledger.core.model.TripType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EvidenceFingerprintTest {

    private final BillingRules rules = BillingRules.defaults();
    private final List<Ping> pings = straightNorth(40, 5, 50);
    private final List<Milestone> milestones = List.of(
            milestone(null, MilestoneType.TRIP_START, OFFICE, 0),
            milestone("EMP-2417", MilestoneType.DROP, north(OFFICE, 1_500), 150),
            new Milestone("EMP-7721", MilestoneType.NO_SHOW, null, null)); // no point, no time - still fine
    private final List<PlannedStop> stops = List.of(planned("EMP-2417", north(OFFICE, 1_500), 150));

    private String fingerprint(List<Ping> p, List<Milestone> m) {
        return EvidenceFingerprint.of(trip(TripType.LOGOUT, p, m, stops), rules, "v1");
    }

    @Test
    void isA64CharacterHexStringAndStableAcrossCalls() {
        String first = fingerprint(pings, milestones);

        assertThat(first.length()).isEqualTo(64);
        assertThat(first.matches("[0-9a-f]{64}")).isTrue();
        assertThat(fingerprint(pings, milestones)).isEqualTo(first);
    }

    @Test
    void uploadOrderDoesNotMatter() {
        List<Ping> shuffledPings = new ArrayList<>(pings);
        List<Milestone> shuffledMilestones = new ArrayList<>(milestones);
        Collections.shuffle(shuffledPings, new Random(7));
        Collections.shuffle(shuffledMilestones, new Random(7));

        assertThat(fingerprint(shuffledPings, shuffledMilestones)).isEqualTo(fingerprint(pings, milestones));
    }

    @Test
    void oneExtraPingChangesIt() {
        List<Ping> more = new ArrayList<>(pings);
        more.add(ping(400, north(OFFICE, 3_000)));

        assertThat(fingerprint(more, milestones).equals(fingerprint(pings, milestones))).isFalse();
    }

    @Test
    void aPingMovedByOneMicroDegreeChangesIt() {
        List<Ping> moved = new ArrayList<>(pings);
        Ping p = moved.get(10);
        moved.set(10, new Ping(p.timestamp(),
                new GeoPoint(p.point().latE6() + 1, p.point().lonE6()),
                p.accuracyMetres(), p.deviceSeq()));

        assertThat(fingerprint(moved, milestones).equals(fingerprint(pings, milestones))).isFalse();
    }

    @Test
    void differentRulesOrRuleVersionChangeIt() {
        TripEvidence evidence = trip(TripType.LOGOUT, pings, milestones, stops);
        String base = EvidenceFingerprint.of(evidence, rules, "v1");

        String stricter = EvidenceFingerprint.of(evidence, rules.toBuilder().maxAccuracyMetres(40).build(), "v1");
        String nextVersion = EvidenceFingerprint.of(evidence, rules, "v2");

        assertThat(stricter.equals(base)).isFalse();
        assertThat(nextVersion.equals(base)).isFalse();
    }

    @Test
    void tripTypeIsPartOfIt() {
        String logout = EvidenceFingerprint.of(trip(TripType.LOGOUT, pings, milestones, stops), rules, "v1");
        String login = EvidenceFingerprint.of(trip(TripType.LOGIN, pings, milestones, stops), rules, "v1");

        assertThat(login.equals(logout)).isFalse();
    }
}
