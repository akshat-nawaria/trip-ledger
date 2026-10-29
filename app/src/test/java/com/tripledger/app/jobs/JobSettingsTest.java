package com.tripledger.app.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JobSettingsTest {

    private final JobSettings settings = new JobSettings(true, 2_000, 20, Duration.ofSeconds(60), 5,
            Duration.ofSeconds(10), Duration.ofMinutes(10), 60_000, 100);

    @Test
    void retriesWaitLongerEachTime() {
        assertThat(settings.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.backoff(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.backoff(3)).isEqualTo(Duration.ofSeconds(90));
        assertThat(settings.backoff(4)).isEqualTo(Duration.ofSeconds(270));
    }

    @Test
    void butNeverLongerThanTheCap() {
        assertThat(settings.backoff(5)).isEqualTo(Duration.ofMinutes(10)); // 810 s would be over 10 min
        assertThat(settings.backoff(50)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void rejectsNonsense() {
        assertThatThrownBy(() -> new JobSettings(true, 2_000, 0, Duration.ofSeconds(60), 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), 60_000, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
