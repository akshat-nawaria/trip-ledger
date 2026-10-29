package com.tripledger.app.api;

import static com.tripledger.app.api.TestEvidence.newKey;
import static com.tripledger.app.api.TestEvidence.newTripId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripledger.app.routing.MockRoutingProvider;
import com.tripledger.app.routing.RoutingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The admin status page and the demo switch for the mock map service (S8).
 *
 * The mock provider is one shared bean for the whole test run, so every test here makes sure it's
 * switched back on afterwards - otherwise some unrelated test later would suddenly get DEGRADED trips.
 */
class AdminIntegrationTest extends ApiTestBase {

    @Autowired
    private RoutingProvider routingProvider;

    @AfterEach
    void mapServiceBackOn() {
        ((MockRoutingProvider) routingProvider).setDown(false);
    }

    @Test
    void statusShowsRoutingHealthAndTheQueue() throws Exception {
        JsonNode status = getJson("/v1/admin/status");

        assertThat(status.get("routing").get("provider").asText()).isEqualTo("mock");
        assertThat(status.get("routing").get("providerDown").asBoolean()).isFalse();
        assertThat(status.get("routing").get("breaker").asText()).isNotBlank();
        assertThat(status.get("jobs").get("pending").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(status.get("jobs").has("oldestPendingSeconds")).isTrue();
    }

    @Test
    void switchingTheMapServiceOffMakesNewTripsDegradedAndOnAgainFixesThat() throws Exception {
        switchMock(admin(), "{\"down\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.providerDown").value(true));

        upload(newTripId(), newKey(), TestEvidence.trip().all())
                .andExpect(jsonPath("$.status").value("DEGRADED"));

        switchMock(admin(), "{\"down\": false}")
                .andExpect(jsonPath("$.routing.providerDown").value(false));

        upload(newTripId(), newKey(), TestEvidence.trip().all())
                .andExpect(jsonPath("$.status").value("COMPUTED"));
    }

    @Test
    void operatorsCannotSeeOrFlipAdminThings() throws Exception {
        getAs(operator(), "/v1/admin/status").andExpect(status().isForbidden());
        switchMock(operator(), "{\"down\": true}").andExpect(status().isForbidden());

        assertThat(((MockRoutingProvider) routingProvider).isDown()).isFalse();
    }

    @Test
    void theSwitchNeedsAnAnswer() throws Exception {
        switchMock(admin(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions switchMock(String authorization, String body) throws Exception {
        return mvc.perform(put("/v1/admin/routing/mock")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
