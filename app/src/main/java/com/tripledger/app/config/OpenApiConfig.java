package com.tripledger.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// the title block of Swagger UI (http://localhost:8080/swagger-ui.html), plus the "Authorize" button
// where you paste a bearer token so the "Try it out" calls are sent signed in
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-token";

    @Bean
    public OpenAPI tripLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TripLedger API")
                        .version("v1")
                        .description("Billable distance for cab trips, rebuilt from unreliable GPS. "
                                + "Upload evidence, then read the km and the segment-by-segment explanation. "
                                + "Locally, get a token from POST /v1/dev/tokens and paste it into Authorize."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
