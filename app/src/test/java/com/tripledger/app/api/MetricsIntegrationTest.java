package com.tripledger.app.api;

import static com.tripledger.app.api.TestEvidence.newKey;
import static com.tripledger.app.api.TestEvidence.newTripId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

/**
 * S8: what Prometheus scrapes, and the demo page.
 *
 * Spring Boot tests switch metric exporters off by default, so @AutoConfigureObservability is what
 * turns the real Prometheus registry back on here (it also means this class gets its own context).
 */
@AutoConfigureObservability
class MetricsIntegrationTest extends ApiTestBase {

    @Test
    void prometheusCanScrapeOurBusinessMetricsWithoutAToken() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        String scrape = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(scrape)
                .contains("tripledger_trips_computed_total{application=\"tripledger\",status=\"COMPUTED\"}")
                .contains("tripledger_segments_total{application=\"tripledger\",rule=\"ROUTED\"}")
                .contains("tripledger_segments_total{application=\"tripledger\",rule=\"SHORT_GAP_AERIAL\"}")
                .contains("tripledger_routing_lookups_total{application=\"tripledger\",source=\"provider\"}")
                .contains("tripledger_ingest_uploads_total{application=\"tripledger\",result=\"accepted\"}")
                .contains("tripledger_compute_duration_seconds_bucket")
                .contains("tripledger_jobs_queue{application=\"tripledger\",status=\"pending\"}")
                .contains("tripledger_jobs_oldest_pending_age_seconds")
                .contains("tripledger_routing_breaker_state")
                .contains("http_server_requests_seconds_bucket");
    }

    @Test
    void metricsNeverCarryTenantOrTripIds() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        String scrape = mvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();

        // one series per trip would eventually choke Prometheus - per-trip detail lives in the DB and logs
        assertThat(scrape).doesNotContain(tripId).doesNotContain(tenant);
    }

    @Test
    void theDemoPageIsServedWithoutSigningIn() throws Exception {
        String page = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("<title>TripLedger Console</title>");

        // "/" is Spring's welcome page, which forwards to index.html
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }
}
