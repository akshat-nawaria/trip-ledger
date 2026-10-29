package com.tripledger.app.api;

import static com.tripledger.app.api.TestEvidence.newKey;
import static com.tripledger.app.api.TestEvidence.newTripId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.routing.MockRoutingProvider;
import com.tripledger.app.routing.RoutingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The trip API end to end, signed in as an operator / admin of this test's own tenant
 * (see ApiTestBase), against the real Postgres from docker-compose ("docker compose up -d" first;
 * CI starts its own).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TripApiIntegrationTest extends ApiTestBase {

    @Autowired
    private RoutingProvider routingProvider;

    // ---- the happy path ----------------------------------------------------------------

    @Test
    void uploadComputesTheTripRightAway() throws Exception {
        String tripId = newTripId();

        upload(tripId, newKey(), TestEvidence.trip().all())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPUTED"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.pingsNew").value(TestEvidence.PINGS))
                .andExpect(jsonPath("$.pingsDuplicate").value(0));

        JsonNode distance = getJson("/v1/trips/{id}/distance", tripId);
        assertThat(distance.get("status").asText()).isEqualTo("COMPUTED");
        assertThat(distance.get("ruleVersion").asText()).isEqualTo("v2");
        assertThat(Double.parseDouble(distance.get("billableKm").asText())).isCloseTo(TestEvidence.EXPECTED_KM, within(0.05));
        assertThat(distance.get("routing").get("requests").asInt()).isEqualTo(1);
        assertThat(distance.get("routing").get("providerCalls").asInt()).isEqualTo(1);
    }

    @Test
    void breakdownExplainsEverySegment() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        JsonNode breakdown = getJson("/v1/trips/{id}/breakdown", tripId);

        // 322 pings + the office anchor = 323 points = 322 segments
        assertThat(breakdown.get("segments").size()).isEqualTo(322);
        assertThat(breakdown.get("ruleCounts").get("ROUTED").asLong()).isEqualTo(1L);
        assertThat(breakdown.get("ruleCounts").get("SHORT_GAP_AERIAL").asLong()).isEqualTo(321L);
        assertThat(breakdown.get("cleaning").get("received").asInt()).isEqualTo(TestEvidence.PINGS);
        assertThat(breakdown.get("cleaning").get("kept").asInt()).isEqualTo(TestEvidence.PINGS);
        assertThat(breakdown.get("anchors").get(0).get("kind").asText()).isEqualTo("OFFICE");
        assertThat(breakdown.get("anchors").get(0).get("source").asText()).isEqualTo("ACTUAL");
    }

    @Test
    void aTripSentInTwoUploadsComesOutTheSameAsOneUpload() throws Exception {
        TestEvidence trip = TestEvidence.trip();
        String inOneGo = newTripId();
        String inTwoParts = newTripId();

        upload(inOneGo, newKey(), trip.all()).andExpect(status().isAccepted());
        upload(inTwoParts, newKey(), trip.firstHalf()).andExpect(status().isAccepted());
        upload(inTwoParts, newKey(), trip.secondHalf())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPUTED"));

        assertThat(getJson("/v1/trips/{id}/distance", inTwoParts).get("totalMm").asLong())
                .isEqualTo(getJson("/v1/trips/{id}/distance", inOneGo).get("totalMm").asLong());
    }

    // ---- retries and duplicates --------------------------------------------------------

    @Test
    void sameKeyAndSameBodyIsAReplayAndChangesNothing() throws Exception {
        String tripId = newTripId();
        String key = newKey();
        EvidenceRequest body = TestEvidence.trip().all();

        upload(tripId, key, body).andExpect(status().isAccepted());
        String runBefore = getJson("/v1/trips/{id}/distance", tripId).get("runId").asText();

        upload(tripId, key, body)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.status").value("COMPUTED"));

        assertThat(getJson("/v1/trips/{id}/distance", tripId).get("runId").asText()).isEqualTo(runBefore);
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() throws Exception {
        String tripId = newTripId();
        String key = newKey();
        TestEvidence trip = TestEvidence.trip();

        upload(tripId, key, trip.firstHalf()).andExpect(status().isAccepted());

        upload(tripId, key, trip.secondHalf())
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void resendingPingsWeAlreadyHaveCountsThemAsDuplicatesAndKeepsTheSameRun() throws Exception {
        String tripId = newTripId();
        EvidenceRequest body = TestEvidence.trip().all();

        upload(tripId, newKey(), body).andExpect(status().isAccepted());
        String runBefore = getJson("/v1/trips/{id}/distance", tripId).get("runId").asText();

        upload(tripId, newKey(), body) // new key, same data - e.g. a device that lost its upload log
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.pingsNew").value(0))
                .andExpect(jsonPath("$.pingsDuplicate").value(TestEvidence.PINGS));

        JsonNode after = getJson("/v1/trips/{id}/distance", tripId);
        assertThat(after.get("runId").asText()).isEqualTo(runBefore); // nothing new -> same run, no new routing calls
    }

    @Test
    void tripDetailsMustMatchTheFirstUpload() throws Exception {
        String tripId = newTripId();
        TestEvidence trip = TestEvidence.trip();

        upload(tripId, newKey(), trip.all()).andExpect(status().isAccepted());

        upload(tripId, newKey(), trip.withDifferentWindow()).andExpect(status().isConflict());
    }

    // ---- routing outage ------------------------------------------------------------------

    @Test
    void providerOutageGivesADegradedTripThatRecoversOnTheNextUpload() throws Exception {
        MockRoutingProvider mock = (MockRoutingProvider) routingProvider;
        String tripId = newTripId();
        EvidenceRequest body = TestEvidence.trip().all();
        try {
            mock.setDown(true);
            upload(tripId, newKey(), body)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("DEGRADED"));
            assertThat(listContains("DEGRADED", tripId)).isTrue();
        } finally {
            mock.setDown(false);
        }

        // nothing new in this upload, but the last run was DEGRADED, so it's worth trying again
        upload(tripId, newKey(), body)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPUTED"));
        assertThat(listContains("DEGRADED", tripId)).isFalse();
    }

    // ---- bad requests --------------------------------------------------------------------

    @Test
    void unknownTripIs404() throws Exception {
        getAs(admin(), "/v1/trips/{id}/distance", newTripId())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        getAs(admin(), "/v1/trips/{id}/breakdown", newTripId()).andExpect(status().isNotFound());
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        mvc.perform(post("/v1/trips/{id}/evidence", newTripId())
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(TestEvidence.trip().all())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bodyWithoutATripTypeIs400() throws Exception {
        EvidenceRequest valid = TestEvidence.trip().all();
        EvidenceRequest noType = new EvidenceRequest(null, valid.windowStart(), valid.windowEnd(),
                valid.plannedOffice(), valid.pings(), valid.milestones(), valid.plannedStops());

        upload(newTripId(), newKey(), noType)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void windowThatEndsBeforeItStartsIs400() throws Exception {
        EvidenceRequest valid = TestEvidence.trip().all();
        EvidenceRequest backwards = new EvidenceRequest(valid.tripType(), valid.windowEnd(), valid.windowStart(),
                valid.plannedOffice(), valid.pings(), valid.milestones(), valid.plannedStops());

        upload(newTripId(), newKey(), backwards).andExpect(status().isBadRequest());
    }

    @Test
    void tripIdWithOddCharactersIs400() throws Exception {
        upload("T.0418", newKey(), TestEvidence.trip().all()).andExpect(status().isBadRequest());
    }

    @Test
    void listLimitOutOfRangeIs400() throws Exception {
        mvc.perform(get("/v1/trips").param("limit", "0").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/trips").param("status", "NOT_A_STATUS").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isBadRequest());
    }

    // ---- docs ------------------------------------------------------------------------------

    @Test
    void openApiSpecListsTheEndpoints() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(spec.contains("/v1/trips/{tripId}/evidence")).isTrue();
        assertThat(spec.contains("/v1/trips/{tripId}/breakdown")).isTrue();
    }

    // ---- helpers ---------------------------------------------------------------------------

    private boolean listContains(String status, String tripId) throws Exception {
        JsonNode trips = json.readTree(mvc.perform(get("/v1/trips").param("status", status).param("limit", "200")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode trip : trips) {
            if (trip.get("tripId").asText().equals(tripId)) {
                return true;
            }
        }
        return false;
    }
}
