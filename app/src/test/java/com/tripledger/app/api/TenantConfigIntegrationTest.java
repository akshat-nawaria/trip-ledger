package com.tripledger.app.api;

import static com.tripledger.app.api.TestEvidence.newKey;
import static com.tripledger.app.api.TestEvidence.newTripId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.security.Role;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// each company's billing settings: versioned, admin-only, separate per tenant, and actually used
@SpringBootTest
@AutoConfigureMockMvc
class TenantConfigIntegrationTest extends ApiTestBase {

    @Test
    void aNewCompanyStartsOnTheCaseStudyDefaults() throws Exception {
        JsonNode config = getJson("/v1/tenant/config");

        assertThat(config.get("version").asInt()).isEqualTo(0);
        assertThat(config.get("noShowAnchorEnabled").asBoolean()).isFalse();
        assertThat(config.get("maxAccuracyMetres").asInt()).isEqualTo(50);
        assertThat(config.get("maxSpeedKmph").asDouble()).isEqualTo(120.0);
        assertThat(config.get("officeRadiusMetres").asInt()).isEqualTo(400);
        assertThat(config.get("maxRouteKm").asInt()).isEqualTo(100);
    }

    @Test
    void everySaveBecomesTheNextVersion() throws Exception {
        save(settings(true, 50)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        save(settings(true, 30)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        JsonNode now = getJson("/v1/tenant/config");
        assertThat(now.get("version").asInt()).isEqualTo(2);
        assertThat(now.get("maxAccuracyMetres").asInt()).isEqualTo(30);
        assertThat(now.get("noShowAnchorEnabled").asBoolean()).isTrue();
    }

    @Test
    void nonsenseSettingsAreRejected() throws Exception {
        save(settings(false, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        Map<String, Object> incomplete = settings(false, 50);
        incomplete.remove("maxRouteKm");
        save(incomplete)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(getJson("/v1/tenant/config").get("version").asInt()).isEqualTo(0); // nothing was saved
    }

    @Test
    void newSettingsApplyFromTheNextUploadAndEachRunRecordsWhichSettingsItUsed() throws Exception {
        String tripId = newTripId();
        EvidenceRequest body = TestEvidence.trip().all();
        upload(tripId, newKey(), body).andExpect(status().isAccepted());
        JsonNode before = getJson("/v1/trips/{id}/distance", tripId);
        assertThat(before.get("configVersion").asInt()).isEqualTo(0);

        // every test ping claims 5 m accuracy; demanding 4 m throws them all out
        save(settings(false, 4)).andExpect(status().isOk());
        upload(tripId, newKey(), body).andExpect(status().isAccepted()); // same data, new settings

        JsonNode after = getJson("/v1/trips/{id}/distance", tripId);
        assertThat(after.get("configVersion").asInt()).isEqualTo(1);
        assertThat(after.get("runId").asText().equals(before.get("runId").asText())).isFalse();
        assertThat(after.get("totalMm").asLong() < before.get("totalMm").asLong()).isTrue();
    }

    @Test
    void settingsBelongToOneCompanyOnly() throws Exception {
        save(settings(true, 25)).andExpect(status().isOk());

        String otherAdmin = bearer("other-" + tenant, Set.of(Role.ADMIN), null);
        JsonNode theirs = body(getAs(otherAdmin, "/v1/tenant/config").andExpect(status().isOk()));
        assertThat(theirs.get("version").asInt()).isEqualTo(0);
        assertThat(theirs.get("maxAccuracyMetres").asInt()).isEqualTo(50);
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private static Map<String, Object> settings(boolean noShow, int maxAccuracyMetres) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("noShowAnchorEnabled", noShow);
        s.put("maxAccuracyMetres", maxAccuracyMetres);
        s.put("maxSpeedKmph", 120.0);
        s.put("officeRadiusMetres", 400);
        s.put("maxRouteKm", 100);
        return s;
    }

    private ResultActions save(Map<String, Object> body) throws Exception {
        return mvc.perform(put("/v1/tenant/config")
                .header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
