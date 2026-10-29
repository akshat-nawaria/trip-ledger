package com.tripledger.app.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tripledger.app.api.Problems;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. Every /v1 endpoint needs a bearer token; the role rules below mirror the
 * table in DESIGN.md section 9. Health, info and the API docs stay open so a demo or a load
 * balancer can reach them without logging in.
 *
 * No sessions and no cookies: every request carries its own token. That's also why CSRF
 * protection is off - CSRF attacks ride on cookies the browser sends by itself, and we have none.
 */
@Configuration
@EnableConfigurationProperties(JwtSettings.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationConverter jwtConverter, Problems problems)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // let Spring's own error forwarding through, otherwise a real error turns into a confusing 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // scraped by Prometheus inside the compose network; carries no tenant or trip data
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // the demo page itself is just static files; every call it makes still needs a token
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/dev/tokens").permitAll() // only exists when dev-tokens is on
                        .requestMatchers(HttpMethod.POST, "/v1/trips/*/evidence").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/trips/*/distance").hasAnyRole("EMPLOYEE", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/trips/*/breakdown").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/trips").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/trips/*/recompute", "/v1/trips/*/bill").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/trips/*/adjustments").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/v1/tenant/config").hasRole("ADMIN")
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(problems::unauthorized)
                        .accessDeniedHandler(problems::forbidden))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problems::unauthorized)
                        .accessDeniedHandler(problems::forbidden));
        return http.build();
    }

    // "roles": ["OPERATOR"] in the token -> Spring authority ROLE_OPERATOR, which hasRole("OPERATOR") checks
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(Claims.ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    // checks signature, expiry, issuer, and that tenant_id + roles are there
    @Bean
    public JwtDecoder jwtDecoder(JwtSettings settings) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(settings.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.issuer()),
                new TenantClaimValidator()));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtSettings settings) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(settings.secretKey()));
    }
}
