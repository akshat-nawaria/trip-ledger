package com.tripledger.app.api;

import static com.tripledger.app.api.TestEvidence.newKey;
import static com.tripledger.app.api.TestEvidence.newTripId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.jobs.RecoveryScanner;
import com.tripledger.app.routing.MockRoutingProvider;
import com.tripledger.app.routing.RoutingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// R10 (recovery), R11 (idempotent recompute), R12 (late pings after billing), plus pinned routes
@SpringBootTest
@AutoConfigureMockMvc
class RecomputeBillingIntegrationTest extends ApiTestBase {

    @Autowired
    private RoutingProvider routingProvider;

    @Autowired
    private RecoveryScanner recovery;

    // ---- R11 -------------------------------------------------------------------------------------------

    @Test
    void recomputingTwiceGivesTheSameRunAndTheSameKm() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());
        JsonNode before = getJson("/v1/trips/{id}/distance", tripId);

        JsonNode first = body(recompute(tripId).andExpect(status().isAccepted()));
        JsonNode second = body(recompute(tripId).andExpect(status().isAccepted()));

        assertThat(first.get("runId").asText()).isEqualTo(before.get("runId").asText());
        assertThat(second.get("runId").asText()).isEqualTo(before.get("runId").asText());
        assertThat(getJson("/v1/trips/{id}/distance", tripId).get("totalMm").asLong())
                .isEqualTo(before.get("totalMm").asLong());
    }

    // ---- R12 -------------------------------------------------------------------------------------------

    @Test
    void latePingsAfterBillingBecomeASignedAdjustmentAndNeverEditTheInvoice() throws Exception {
        TestEvidence trip = TestEvidence.trip();
        String tripId = newTripId();
        upload(tripId, newKey(), trip.firstHalf()).andExpect(status().isAccepted());
        JsonNode billed = body(bill(tripId, "INV-1001").andExpect(status().isOk()));

        upload(tripId, newKey(), trip.secondHalf()).andExpect(status().isAccepted()); // the late pings
        upload(tripId, newKey(), trip.secondHalf()).andExpect(status().isAccepted()); // ...and a re-send of them

        JsonNode adj = getJson("/v1/trips/{id}/adjustments", tripId);
        JsonNode now = getJson("/v1/trips/{id}/distance", tripId);
        assertThat(adj.get("invoiceId").asText()).isEqualTo("INV-1001");
        assertThat(adj.get("billedKm").asText()).isEqualTo(billed.get("billedKm").asText()); // invoice untouched
        assertThat(adj.get("adjustments").size()).isEqualTo(1);                             // re-send added nothing
        assertThat(adj.get("adjustments").get(0).get("deltaKm").asText().startsWith("+")).isTrue();
        assertThat(adj.get("chargedKm").asText()).isEqualTo(now.get("billableKm").asText()); // invoice + delta = truth
    }

    @Test
    void billingIsOncePerTripTheSameInvoiceAgainIsANoOp() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        JsonNode first = body(bill(tripId, "INV-7").andExpect(status().isOk()));
        JsonNode again = body(bill(tripId, "INV-7").andExpect(status().isOk()));
        bill(tripId, "INV-8")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BILLING_CONFLICT"));

        assertThat(again.get("billedAt").asText()).isEqualTo(first.get("billedAt").asText());
        bill(newTripId(), "INV-9").andExpect(status().isNotFound());
    }

    @Test
    void anUnbilledTripHasNoAdjustments() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        JsonNode adj = getJson("/v1/trips/{id}/adjustments", tripId);

        assertThat(adj.get("invoiceId").isNull()).isTrue();
        assertThat(adj.get("adjustments").size()).isEqualTo(0);
    }

    // ---- R10 + R12 together -------------------------------------------------------------------------

    @Test
    void aTripBilledWhileDegradedIsCorrectedOnceRoutingRecovers() throws Exception {
        MockRoutingProvider mock = (MockRoutingProvider) routingProvider;
        String tripId = newTripId();
        try {
            mock.setDown(true);
            upload(tripId, newKey(), TestEvidence.trip().all())
                    .andExpect(jsonPath("$.status").value("DEGRADED"));
        } finally {
            mock.setDown(false);
        }
        bill(tripId, "INV-PROVISIONAL").andExpect(status().isOk()); // billed on the x1.4 guess

        assertThat(recovery.scanOnce() >= 1).isTrue();               // routing healthy -> re-queue DEGRADED trips
        drainJobs();

        JsonNode now = getJson("/v1/trips/{id}/distance", tripId);
        JsonNode adj = getJson("/v1/trips/{id}/adjustments", tripId);
        assertThat(now.get("status").asText()).isEqualTo("COMPUTED");
        assertThat(adj.get("adjustments").size()).isEqualTo(1);
        // the road (x1.3) is shorter than the straight-line guess (x1.4), so the correction is negative
        assertThat(adj.get("adjustments").get(0).get("deltaKm").asText().startsWith("-")).isTrue();
        assertThat(adj.get("chargedKm").asText()).isEqualTo(now.get("billableKm").asText());
    }

    // ---- pinned routes -------------------------------------------------------------------------------

    @Test
    void aRecomputeDuringAnOutageReusesTheRoadsItAlreadyPaidFor() throws Exception {
        MockRoutingProvider mock = (MockRoutingProvider) routingProvider;
        String tripId = newTripId();
        EvidenceRequest body = TestEvidence.trip().all();
        upload(tripId, newKey(), body).andExpect(status().isAccepted());
        long kmBefore = getJson("/v1/trips/{id}/distance", tripId).get("totalMm").asLong();

        // force a genuine recompute (new settings = new fingerprint) while the provider is down
        saveSettingsWithOfficeRadius(399);
        try {
            mock.setDown(true);
            upload(tripId, newKey(), body).andExpect(jsonPath("$.status").value("COMPUTED")); // not DEGRADED
        } finally {
            mock.setDown(false);
        }

        JsonNode after = getJson("/v1/trips/{id}/distance", tripId);
        assertThat(after.get("routing").get("replayed").asInt()).isEqualTo(1);
        assertThat(after.get("routing").get("providerCalls").asInt()).isEqualTo(0);
        assertThat(after.get("totalMm").asLong()).isEqualTo(kmBefore);
    }

    // ---- who may do what -----------------------------------------------------------------------------

    @Test
    void onlyAdminsRecomputeOrBillAndEmployeesNeverSeeAdjustments() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());

        mvc.perform(post("/v1/trips/{id}/recompute", tripId).header(HttpHeaders.AUTHORIZATION, operator()))
                .andExpect(status().isForbidden());
        bill(tripId, "INV-X", operator()).andExpect(status().isForbidden());
        getAs(employee("e-1"), "/v1/trips/{id}/adjustments", tripId).andExpect(status().isForbidden());
        getAs(operator(), "/v1/trips/{id}/adjustments", tripId).andExpect(status().isOk());
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private ResultActions recompute(String tripId) throws Exception {
        return mvc.perform(post("/v1/trips/{id}/recompute", tripId).header(HttpHeaders.AUTHORIZATION, admin()));
    }

    private ResultActions bill(String tripId, String invoiceId) throws Exception {
        return bill(tripId, invoiceId, admin());
    }

    private ResultActions bill(String tripId, String invoiceId, String authorization) throws Exception {
        return mvc.perform(post("/v1/trips/{id}/bill", tripId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invoiceId\":\"" + invoiceId + "\"}"));
    }

    private void saveSettingsWithOfficeRadius(int metres) throws Exception {
        mvc.perform(put("/v1/tenant/config").header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noShowAnchorEnabled\":false,\"maxAccuracyMetres\":50,\"maxSpeedKmph\":120,"
                                + "\"officeRadiusMetres\":" + metres + ",\"maxRouteKm\":100}"))
                .andExpect(status().isOk());
    }
}
