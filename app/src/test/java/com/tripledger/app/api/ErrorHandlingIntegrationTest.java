package com.tripledger.app.api;

import static com.tripledger.app.api.TestEvidence.newKey;
import static com.tripledger.app.api.TestEvidence.newTripId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripledger.app.api.dto.EvidenceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

// every error looks the same, carries a code + trace id, and gives nothing away
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ErrorHandlingIntegrationTest extends ApiTestBase {

    @Test
    void everyResponseCarriesATraceIdHeader() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(header().exists(TraceIds.HEADER));
    }

    @Test
    void anUnexpectedErrorIs500AndLeaksNothing(CapturedOutput logs) throws Exception {
        MvcResult result = getAs(admin(), "/v1/test/boom")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String traceId = result.getResponse().getHeader(TraceIds.HEADER);
        assertThat(body.contains("SELECT")).isFalse();
        assertThat(body.contains("IllegalStateException")).isFalse();
        assertThat(body.contains("at com.")).isFalse();
        assertThat(json.readTree(body).get("traceId").asText()).isEqualTo(traceId);
        // ...but the real cause IS in our own log, findable by that same trace id
        assertThat(logs.getOut().contains("traceId=" + traceId)).isTrue();
    }

    @Test
    void validationErrorsGetACodeAndATraceId() throws Exception {
        EvidenceRequest valid = TestEvidence.trip().all();
        EvidenceRequest noType = new EvidenceRequest(null, valid.windowStart(), valid.windowEnd(),
                valid.plannedOffice(), valid.pings(), valid.milestones(), valid.plannedStops());

        upload(newTripId(), newKey(), noType)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void brokenJsonIsMalformedBody() throws Exception {
        mvc.perform(post("/v1/trips/{id}/evidence", newTripId())
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void aMissingIdempotencyKeyIsMissingHeader() throws Exception {
        mvc.perform(post("/v1/trips/{id}/evidence", newTripId())
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(TestEvidence.trip().all())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void ourOwnErrorsHaveTheirOwnCodes() throws Exception {
        getAs(admin(), "/v1/trips/{id}/distance", newTripId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        getAs(admin(), "/v1/trips/{id}/distance", "bad.id")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void anUnknownPathIsAPlain404() throws Exception {
        getAs(admin(), "/v1/no-such-thing")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void securityErrorsHaveTheSameShapeAndTheSameTraceId() throws Exception {
        MvcResult result = mvc.perform(get("/v1/trips")).andExpect(status().isUnauthorized()).andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("traceId").asText()).isEqualTo(result.getResponse().getHeader(TraceIds.HEADER));
    }

    @Test
    void logsNeverContainEmployeeRefsOrHomeCoordinates(CapturedOutput logs) throws Exception {
        TestEvidence trip = TestEvidence.trip();
        String tripId = newTripId();

        upload(tripId, newKey(), trip.withDropOf("e-PRIVATE-77")).andExpect(status().isAccepted());
        getAs(admin(), "/v1/trips/{id}/breakdown", tripId).andExpect(status().isOk());
        getAs(employee("e-PRIVATE-77"), "/v1/trips/{id}/distance", tripId).andExpect(status().isOk());

        assertThat(logs.getOut().contains("e-PRIVATE-77")).isFalse();
        assertThat(logs.getOut().contains(trip.dropLatitude())).isFalse();
    }
}
