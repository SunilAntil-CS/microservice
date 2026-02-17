package com.vnfm.lcm.infrastructure.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;

/**
 * STUDY NOTE – Idempotency filter (northbound API)
 * ------------------------------------------------
 * Ensures duplicate requests (same requestId) return the cached response without
 * re-executing the operation. Extracts requestId from header X-Request-Id or from
 * JSON body (requestId field). If found in processed_requests, returns cached JSON;
 * otherwise proceeds and, on 2xx success, caches the response.
 *
 * Uses OncePerRequestFilter so the logic runs once per request. ContentCaching*
 * wrappers allow us to read request body for requestId and response body for caching
 * without breaking the downstream chain.
 */
@Component
@Order(1)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ProcessedRequestRepository processedRequestRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(ProcessedRequestRepository processedRequestRepository, ObjectMapper objectMapper) {
        this.processedRequestRepository = processedRequestRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!shouldApply(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            // Extract requestId from header or body (reading body populates the request cache for re-read)
            String requestId = extractRequestId(requestWrapper);
            if (requestId != null && !requestId.isBlank()) {
                Optional<ProcessedRequest> cached = processedRequestRepository.findByRequestId(requestId.trim());
                if (cached.isPresent()) {
                    log.debug("Returning cached response for requestId={}", requestId);
                    applyCachedResponse(responseWrapper, cached.get().getResponseCache());
                    responseWrapper.copyBodyToResponse();
                    return;
                }
            }

            filterChain.doFilter(requestWrapper, responseWrapper);

            // After successful handling: cache response for this requestId (2xx only), including status and headers for 201/202
            if (requestId != null && !requestId.isBlank() && responseWrapper.getStatus() >= 200 && responseWrapper.getStatus() < 300) {
                String cacheJson = buildResponseCache(responseWrapper);
                if (cacheJson != null) {
                    processedRequestRepository.save(new ProcessedRequest(requestId.trim(), cacheJson, Instant.now()));
                    log.debug("Cached response for requestId={}", requestId);
                }
            }
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }

    /** Apply to API paths that support idempotency: /api/** and POST /vnflcm/v1/vnf_instances, POST /vnflcm/v1/vnf_instances/{id}/instantiate. */
    private boolean shouldApply(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return false;
        if (path.startsWith("/api/")) return true;
        if (path.startsWith("/vnflcm/v1/")) {
            if ("POST".equalsIgnoreCase(request.getMethod()) && path.equals("/vnflcm/v1/vnf_instances")) return true;
            if ("POST".equalsIgnoreCase(request.getMethod()) && path.matches("/vnflcm/v1/vnf_instances/[^/]+/instantiate")) return true;
        }
        return false;
    }

    /** Apply cached response: supports structured cache (status + headers + body) or legacy body-only. */
    private void applyCachedResponse(ContentCachingResponseWrapper response, String cacheJson) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            JsonNode root = objectMapper.readTree(cacheJson);
            if (root != null && root.isObject() && root.has("status")) {
                int status = root.get("status").asInt();
                response.setStatus(status);
                if (root.has("headers") && root.get("headers").isObject()) {
                    JsonNode headers = root.get("headers");
                    Iterator<String> names = headers.fieldNames();
                    while (names.hasNext()) {
                        String name = names.next();
                        if ("Location".equalsIgnoreCase(name)) {
                            response.setHeader("Location", headers.get(name).asText());
                        }
                    }
                }
                if (root.has("body") && !root.get("body").isNull()) {
                    response.setContentType("application/json");
                    String body = root.get("body").isTextual() ? root.get("body").asText() : objectMapper.writeValueAsString(root.get("body"));
                    response.getWriter().write(body);
                }
                return;
            }
        } catch (Exception e) {
            log.trace("Cache not in structured format, using as body: {}", e.getMessage());
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write(cacheJson);
    }

    /** Build cache JSON: for 201/202 include status and Location header so duplicate requests get same response. */
    private String buildResponseCache(ContentCachingResponseWrapper response) {
        try {
            int status = response.getStatus();
            byte[] bodyBytes = response.getContentAsByteArray();
            String body = (bodyBytes != null && bodyBytes.length > 0) ? new String(bodyBytes, StandardCharsets.UTF_8) : null;
            if (status == 201 || status == 202) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("status", status);
                ObjectNode headers = objectMapper.createObjectNode();
                String location = response.getHeader("Location");
                if (location != null && !location.isBlank()) {
                    headers.put("Location", location);
                }
                node.set("headers", headers);
                if (body != null) node.put("body", body); else node.putNull("body");
                return objectMapper.writeValueAsString(node);
            }
            if (body != null && !body.isBlank()) {
                return body;
            }
        } catch (Exception e) {
            log.warn("Could not build response cache: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract requestId from X-Request-Id header, or from JSON body (requestId field).
     * Reading the request input stream populates ContentCachingRequestWrapper so the controller can re-read.
     */
    private String extractRequestId(ContentCachingRequestWrapper request) throws IOException {
        String fromHeader = request.getHeader(REQUEST_ID_HEADER);
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim();
        }
        // For POST/PUT/PATCH with JSON body, try to get requestId from body.
        // Reading the stream populates the wrapper cache so the controller can re-read the body.
        String method = request.getMethod();
        if (method != null && (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT") || method.equalsIgnoreCase("PATCH"))) {
            byte[] content = readRequestBody(request);
            if (content != null && content.length > 0) {
                try {
                    JsonNode root = objectMapper.readTree(content);
                    if (root != null && root.has("requestId")) {
                        JsonNode node = root.get("requestId");
                        if (node != null && !node.isNull()) {
                            return node.asText();
                        }
                    }
                } catch (Exception e) {
                    log.trace("Could not parse body for requestId: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    /** Read request body and cache it in the wrapper so the controller can re-read. */
    private byte[] readRequestBody(ContentCachingRequestWrapper request) throws IOException {
        byte[] content = request.getContentAsByteArray();
        if (content != null && content.length > 0) {
            return content;
        }
        try (var is = request.getInputStream()) {
            content = is.readAllBytes();
        }
        return content.length > 0 ? content : null;
    }
}
