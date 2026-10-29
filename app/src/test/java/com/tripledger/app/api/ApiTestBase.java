package com.tripledger.app.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripledger.app.api.dto.EvidenceRequest;
import com.tripledger.app.jobs.ComputeWorker;
import com.tripledger.app.security.Role;
import com.tripledger.app.security.TokenIssuer;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared plumbing for the API tests: the app with real security, real Postgres (docker compose
 * locally, a service container in CI), and helpers to call it signed in.
 *
 * Every test gets its own brand-new tenant (JUnit makes a fresh instance per test), so tests can
 * never see each other's trips or settings - and the database can be reused between runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected TokenIssuer tokens;

    @Autowired
    protected ComputeWorker worker;

    protected final String tenant = "it-" + UUID.randomUUID().toString().substring(0, 8);

    protected String operator() {
        return bearer(tenant, Set.of(Role.OPERATOR), null);
    }

    protected String admin() {
        return bearer(tenant, Set.of(Role.ADMIN), null);
    }

    protected String employee(String employeeRef) {
        return bearer(tenant, Set.of(Role.EMPLOYEE), employeeRef);
    }

    protected String bearer(String tenantId, Set<Role> roles, String employeeRef) {
        return "Bearer " + tokens.issue(tenantId, "test-user", roles, employeeRef).token();
    }

    // uploads as an operator of this test's tenant
    protected ResultActions upload(String tripId, String key, EvidenceRequest body) throws Exception {
        return upload(tripId, key, body, operator());
    }

    protected ResultActions upload(String tripId, String key, EvidenceRequest body, String authorization) throws Exception {
        return mvc.perform(post("/v1/trips/{id}/evidence", tripId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected ResultActions getAs(String authorization, String url, Object... vars) throws Exception {
        return mvc.perform(get(url, vars).header(HttpHeaders.AUTHORIZATION, authorization));
    }

    // reads as an admin of this test's tenant and expects 200
    protected JsonNode getJson(String url, Object... vars) throws Exception {
        String body = getAs(admin(), url, vars)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    // the background worker is switched off in tests (src/test/resources/application.properties);
    // this runs it by hand until nothing is due any more
    protected void drainJobs() {
        for (int i = 0; i < 100 && worker.runOnce() > 0; i++) {
            // keep going
        }
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
