package com.tripledger.app.api;

import com.tripledger.app.security.Role;
import com.tripledger.app.security.TokenIssuer;
import com.tripledger.app.service.RequestChecks;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands out a token for any tenant and role - so you can try the API locally without a real
 * login system. It only exists when tripledger.security.dev-tokens=true; in any real environment
 * that flag is false, this controller isn't even created, and the URL is a plain 404.
 */
@RestController
@RequestMapping("/v1/dev/tokens")
@ConditionalOnProperty(prefix = "tripledger.security", name = "dev-tokens", havingValue = "true")
@Tag(name = "Dev tokens", description = "LOCAL DEMO ONLY: get a token for any tenant and role")
public class DevTokenController {

    private final TokenIssuer tokens;

    public DevTokenController(TokenIssuer tokens) {
        this.tokens = tokens;
    }

    public record DevTokenRequest(
            @NotBlank @Schema(example = "acme") String tenant,
            @NotBlank @Schema(example = "priya.ops") String subject,
            @NotEmpty @Schema(example = "[\"OPERATOR\"]") Set<Role> roles,
            @Schema(description = "only for EMPLOYEE tokens: which employee this is", example = "e-104") String employeeRef) {
    }

    public record DevTokenResponse(String token, Instant expiresAt) {
    }

    @PostMapping
    @Operation(summary = "Get a signed token (local demo only)",
            description = "Paste the token into Swagger's Authorize button, or send it as 'Authorization: Bearer <token>'.")
    public DevTokenResponse issue(@Valid @RequestBody DevTokenRequest request) {
        RequestChecks.tenantId(request.tenant());
        TokenIssuer.IssuedToken issued = tokens.issue(request.tenant(), request.subject(), request.roles(), request.employeeRef());
        return new DevTokenResponse(issued.token(), issued.expiresAt());
    }
}
