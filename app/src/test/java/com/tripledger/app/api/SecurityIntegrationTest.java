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
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tripledger.app.security.Claims;
import com.tripledger.app.security.Role;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

// who gets in, who gets what, and that one company can never see another's trips
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends ApiTestBase {

    @Autowired
    private JwtEncoder ourEncoder;

    // ---- bad or missing tokens -> 401 --------------------------------------------------------

    @Test
    void noTokenIs401WithAProblemBody() throws Exception {
        mvc.perform(get("/v1/trips/{id}/distance", newTripId()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void tokenSignedWithSomeoneElsesSecretIs401() throws Exception {
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "a-completely-different-secret-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = "Bearer " + sign(forger, claims("tripledger", tenant, List.of("ADMIN")));

        getAs(forged, "/v1/trips").andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        // issued an hour ago, valid for 10 minutes -> expired 50 minutes ago (well past the 60 s clock-skew allowance)
        Instant anHourAgo = Instant.now().minus(Duration.ofHours(1));
        String expired = "Bearer " + tokens.issue(tenant, "late", Set.of(Role.ADMIN), null, anHourAgo, Duration.ofMinutes(10)).token();

        getAs(expired, "/v1/trips").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutATenantIs401() throws Exception {
        JwtClaimsSet noTenant = JwtClaimsSet.builder().issuer("tripledger").subject("x")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim(Claims.ROLES, List.of("ADMIN")).build();

        getAs("Bearer " + sign(ourEncoder, noTenant), "/v1/trips").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithAnUnknownRoleIs401() throws Exception {
        getAs("Bearer " + sign(ourEncoder, claims("tripledger", tenant, List.of("SUPERUSER"))), "/v1/trips")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIs401() throws Exception {
        getAs("Bearer " + sign(ourEncoder, claims("someone-else", tenant, List.of("ADMIN"))), "/v1/trips")
                .andExpect(status().isUnauthorized());
    }

    // ---- roles -> 403 ---------------------------------------------------------------------------

    @Test
    void anEmployeeCannotUploadEvidence() throws Exception {
        upload(newTripId(), newKey(), TestEvidence.trip().all(), employee("EMP-2417"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void onlyAdminsCanListTripsOrTouchSettings() throws Exception {
        getAs(operator(), "/v1/trips").andExpect(status().isForbidden());
        getAs(operator(), "/v1/tenant/config").andExpect(status().isForbidden());

        getAs(admin(), "/v1/trips").andExpect(status().isOk());
        getAs(admin(), "/v1/tenant/config").andExpect(status().isOk());
    }

    @Test
    void anEmployeeSeesTheirOwnTripOnlyAndNeverTheBreakdown() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().withDropOf("EMP-2417")).andExpect(status().isAccepted());

        getAs(employee("EMP-2417"), "/v1/trips/{id}/distance", tripId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPUTED"));
        getAs(employee("EMP-5530"), "/v1/trips/{id}/distance", tripId)
                .andExpect(status().isNotFound()); // not "forbidden": we don't confirm the trip exists
        getAs(employee("EMP-2417"), "/v1/trips/{id}/breakdown", tripId)
                .andExpect(status().isForbidden());
    }

    // ---- tenant isolation -------------------------------------------------------------------------

    @Test
    void anotherCompanyCannotSeeATripAndCanUseTheSameTripIdForItsOwn() throws Exception {
        String tripId = newTripId();
        upload(tripId, newKey(), TestEvidence.trip().all()).andExpect(status().isAccepted());
        long ourKm = getJson("/v1/trips/{id}/distance", tripId).get("totalMm").asLong();

        String otherCompany = "other-" + tenant;
        String otherAdmin = bearer(otherCompany, Set.of(Role.ADMIN), null);
        getAs(otherAdmin, "/v1/trips/{id}/distance", tripId).andExpect(status().isNotFound());
        getAs(otherAdmin, "/v1/trips/{id}/breakdown", tripId).andExpect(status().isNotFound());

        // same trip id, different company, different trip - no clash, no mix-up
        upload(tripId, newKey(), TestEvidence.trip().firstHalf(), bearer(otherCompany, Set.of(Role.OPERATOR), null))
                .andExpect(status().isAccepted());
        assertThat(getJson("/v1/trips/{id}/distance", tripId).get("totalMm").asLong()).isEqualTo(ourKm);
    }

    // ---- open endpoints and dev tokens ----------------------------------------------------------------

    @Test
    void healthInfoAndApiDocsNeedNoToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void devTokenEndpointHandsOutAWorkingToken() throws Exception {
        String request = json.writeValueAsString(java.util.Map.of(
                "tenant", tenant, "subject", "demo-admin", "roles", List.of("ADMIN")));

        JsonNode issued = body(mvc.perform(post("/v1/dev/tokens")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()));

        getAs("Bearer " + issued.get("token").asText(), "/v1/trips").andExpect(status().isOk());
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private static JwtClaimsSet claims(String issuer, String tenantId, List<String> roles) {
        return JwtClaimsSet.builder().issuer(issuer).subject("tester")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim(Claims.TENANT, tenantId).claim(Claims.ROLES, roles).build();
    }

    private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
