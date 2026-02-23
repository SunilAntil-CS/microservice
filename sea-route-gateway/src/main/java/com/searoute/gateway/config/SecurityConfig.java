package com.searoute.gateway.config;

import com.searoute.gateway.security.CustomAccessDeniedHandler;
import com.searoute.gateway.security.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures WebFlux security for the gateway.
 * <p>
 * JWT validation ensures that every request to protected routes is authenticated:
 * the OAuth2 resource server validates the Bearer token (signature, issuer, expiry)
 * using the configured issuer URI. Public keys are resolved via OpenID Connect
 * discovery (or the configured JWK Set URI) and are cached automatically by the
 * decoder, so we do not need to validate tokens manually.
 * <p>
 * Public endpoints (e.g. {@code /actuator/health}) are permitted without a token
 * so that load balancers and orchestrators can perform health checks. All other
 * exchanges require an authenticated JWT.
 * <p>
 * Custom entry point and access denied handler ensure 401/403 responses are
 * returned as consistent JSON (see Step 4a in FLOW.md).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    public SecurityConfig(CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
                          CustomAccessDeniedHandler customAccessDeniedHandler) {
        this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
        this.customAccessDeniedHandler = customAccessDeniedHandler;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // Health and other public endpoints: no authentication required.
                        .pathMatchers("/actuator/health").permitAll()
                        // All other routes require a valid JWT.
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(customAuthenticationEntryPoint))
                .exceptionHandling(handling -> handling
                        .accessDeniedHandler(customAccessDeniedHandler))
                .build();
    }
}
