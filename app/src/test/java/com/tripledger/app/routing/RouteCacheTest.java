package com.tripledger.app.routing;

import static com.tripledger.app.routing.TestRouting.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.tripledger.core.geo.Distance;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RouteCacheTest {

    private final RouteCache cache = new RouteCache(100, Duration.ofHours(1));
    private final RouteKey key = RouteKey.snap(place(0), place(3), 200);
    private final RouteKey otherKey = RouteKey.snap(place(0), place(5), 200);
    private final AtomicInteger loads = new AtomicInteger();

    private Distance load(RouteKey k) {
        loads.incrementAndGet();
        return Distance.ofMetres(3_900);
    }

    @Test
    void firstAskLoadsSecondAskComesFromTheCache() {
        RouteCache.Result first = cache.get(key, this::load);
        RouteCache.Result second = cache.get(key, this::load);

        assertThat(first.fromCache()).isFalse();
        assertThat(second.fromCache()).isTrue();
        assertThat(second.distance()).isEqualTo(first.distance());
        assertThat(loads.get()).isEqualTo(1);
        assertThat(cache.hitRate()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void differentKeysDoNotShareAnswers() {
        cache.get(key, this::load);
        cache.get(otherKey, this::load);

        assertThat(loads.get()).isEqualTo(2);
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void aFailedLoadIsNotRemembered() {
        assertThatThrownBy(() -> cache.get(key, k -> {
            throw new RoutingException("provider down");
        })).isInstanceOf(RoutingException.class);

        RouteCache.Result retry = cache.get(key, this::load);

        assertThat(retry.fromCache()).isFalse();
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void rejectsAZeroSizeCache() {
        assertThatThrownBy(() -> new RouteCache(0, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
