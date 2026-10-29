package com.tripledger.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// the title block at the top of Swagger UI (http://localhost:8080/swagger-ui.html)
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tripLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TripLedger API")
                .version("v1")
                .description("Billable distance for cab trips, rebuilt from unreliable GPS. "
                        + "Upload evidence, then read the km and the segment-by-segment explanation."));
    }
}
