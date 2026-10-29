package com.tripledger.core.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DistanceTest {

    @Test
    void convertsMetresToWholeMillimetres() {
        assertThat(Distance.ofMetres(12.5).millimetres()).isEqualTo(12_500L);
        assertThat(Distance.ofMetres(0.0).millimetres()).isEqualTo(0L);
    }

    @Test
    void roundsToTheNearestMillimetre() {
        assertThat(Distance.ofMetres(1.2346).millimetres()).isEqualTo(1_235L);
        assertThat(Distance.ofMetres(1.2344).millimetres()).isEqualTo(1_234L);
    }

    @Test
    void rejectsNegativeOrNonFiniteMetres() {
        assertThatThrownBy(() -> Distance.ofMetres(-1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Distance.ofMetres(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Distance.ofMetres(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Distance.ofMillimetres(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsExactly() {
        Distance total = Distance.ofMillimetres(1_234).plus(Distance.ofMillimetres(766));

        assertThat(total).isEqualTo(Distance.ofMillimetres(2_000));
    }

    @Test
    void billableKmRoundsOnceHalfEvenToTenMetres() {
        assertThat(Distance.ofMillimetres(30_605_000).toBillableKm()).isEqualTo(new BigDecimal("30.60"));
        assertThat(Distance.ofMillimetres(30_615_000).toBillableKm()).isEqualTo(new BigDecimal("30.62"));
        assertThat(Distance.ofMillimetres(30_606_000).toBillableKm()).isEqualTo(new BigDecimal("30.61"));
        assertThat(Distance.ofMillimetres(30_604_999).toBillableKm()).isEqualTo(new BigDecimal("30.60"));
        assertThat(Distance.ZERO.toBillableKm()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void sumDoesNotDependOnOrder() {
        Random random = new Random(42);
        List<Distance> segments = new ArrayList<>();
        for (int i = 0; i < 1_500; i++) {
            segments.add(Distance.ofMillimetres(random.nextInt(50_000)));
        }
        Distance expected = Distance.sum(segments);

        for (int round = 0; round < 100; round++) {
            Collections.shuffle(segments, random);
            assertThat(Distance.sum(segments)).isEqualTo(expected);
        }
    }

    @Test
    void comparesByLength() {
        assertThat(Distance.ofMillimetres(10).compareTo(Distance.ofMillimetres(20))).isLessThan(0);
        assertThat(Distance.ofMillimetres(20).compareTo(Distance.ofMillimetres(20))).isEqualTo(0);
    }
}
