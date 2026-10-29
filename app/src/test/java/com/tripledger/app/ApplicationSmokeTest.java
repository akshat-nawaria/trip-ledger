package com.tripledger.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// quick sanity check: app boots, says it's healthy, and can see the core module
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoEndpointShowsRuleSetVersionFromCore() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleSetVersion").value("v2"));
    }

    // proves the routing beans wire up from application.yml and the breaker starts closed
    @Test
    void infoEndpointShowsTheRoutingSetup() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.provider").value("mock"))
                .andExpect(jsonPath("$.routing.breaker").value("CLOSED"));
    }
}
