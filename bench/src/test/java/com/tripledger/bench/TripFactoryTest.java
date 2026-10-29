package com.tripledger.bench;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripledger.core.model.Ping;
import com.tripledger.core.model.TripEvidence;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

// the benchmark is only worth something if its trips are realistic AND the same on every machine
class TripFactoryTest {

    private final TripFactory factory = new TripFactory(20_261_029L, 400);

    @Test
    void sameIndexSameTripEveryTime() {
        assertThat(new TripFactory(20_261_029L, 400).trip(42)).isEqualTo(factory.trip(42));
        assertThat(factory.trip(42)).isNotEqualTo(factory.trip(43));
    }

    @Test
    void tripsAreAboutFifteenHundredPings() {
        long total = 0;
        for (int i = 0; i < 200; i++) {
            int pings = factory.trip(i).pings().size();
            assertThat(pings).isBetween(700, 3_000);
            total += pings;
        }
        assertThat(total / 200).isBetween(1_300L, 1_800L);
    }

    @Test
    void everyTripHasAtLeastOneRealGpsGap() {
        // the dead zones (underpasses, tunnels) are what makes the engine ask for a road distance
        for (int i = 0; i < 50; i++) {
            List<Ping> sorted = factory.trip(i).pings().stream().sorted(Comparator.comparing(Ping::timestamp)).toList();
            boolean hasGap = false;
            for (int k = 1; k < sorted.size() && !hasGap; k++) {
                hasGap = Duration.between(sorted.get(k - 1).timestamp(), sorted.get(k).timestamp()).toSeconds() >= 70;
            }
            assertThat(hasGap).isTrue();
        }
    }

    @Test
    void tripsCarryDropsAndAPlanLikeTheRealApp() {
        TripEvidence trip = factory.trip(7);

        assertThat(trip.milestones()).isNotEmpty();
        assertThat(trip.plannedStops()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(trip.plannedOffice()).isEqualTo(TripFactory.OFFICE);
    }
}
