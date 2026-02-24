package com.searoute.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Global filter that propagates user context from the validated JWT to downstream
 * services via request headers.
 * <p>
 * JWT validation is performed by Spring Security's OAuth2 resource server (see
 * {@link com.searoute.gateway.config.SecurityConfig}). The decoder is configured
 * with the issuer URI from application properties; it caches public keys from the
 * JWK Set automatically, so we do not need to validate tokens in this filter.
 * <p>
 * This filter runs after authentication. When the principal is a {@link Jwt}, we
 * extract the subject (user identifier) and roles from claims and add them as
 * {@code X-User-ID} and {@code X-User-Roles} so that backend services can identify
 * the caller without parsing the token again. Downstream services can trust these
 * headers because the gateway has already validated the JWT.
 * <p>
 * Order is set to run after the security filter chain but before route filters
 * (e.g. after {@link CorrelationIdFilter} and {@link LoggingFilter} in the
 * gateway filter chain).
 */
@Component
public class JwtAuthenticationFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

    /** Header sent to backends: authenticated user's identifier (JWT {@code sub} claim). */
    public static final String USER_ID_HEADER = "X-User-ID";

    /** Header sent to backends: comma-separated list of roles from the JWT. */
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    private static final int ORDER_AFTER_SECURITY_AND_LOGGING = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(this::isJwtAuthentication)
                .map(Authentication::getPrincipal)
                .cast(Jwt.class)
                .flatMap(jwt -> chain.filter(mutateRequestWithUserContext(exchange, jwt)))
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return ORDER_AFTER_SECURITY_AND_LOGGING;
    }

    private boolean isJwtAuthentication(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof Jwt;
    }

    /**
     * Builds an exchange with the same request but with {@code X-User-ID} and
     * {@code X-User-Roles} added from the JWT claims. Roles are taken from
     * common IdP claim shapes (e.g. Keycloak {@code realm_access.roles},
     * or {@code roles}, or space-separated {@code scope}).
     */
    private ServerWebExchange mutateRequestWithUserContext(ServerWebExchange exchange, Jwt jwt) {
        String userId = jwt.getSubject();
        String roles = extractRoles(jwt);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(USER_ID_HEADER, userId != null ? userId : "")
                .header(USER_ROLES_HEADER, roles)
                .build();

        return exchange.mutate().request(mutatedRequest).build();
    }

    @SuppressWarnings("unchecked")
    private String extractRoles(Jwt jwt) {
        // Keycloak: realm_access.roles
        Object realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object roles = ((java.util.Map<String, Object>) realmAccess).get("roles");
            if (roles instanceof Collection) {
                return stringifyRoles((Collection<?>) roles);
            }
        }
        // Common: "roles" or "scope" (space-separated)
        List<String> rolesClaim = jwt.getClaimAsStringList("roles");
        if (rolesClaim != null && !rolesClaim.isEmpty()) {
            return stringifyRoles(rolesClaim);
        }
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            return Stream.of(scope.split("\\s+")).collect(Collectors.joining(","));
        }
        return "";
    }

    private static String stringifyRoles(Collection<?> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return roles.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }
}
