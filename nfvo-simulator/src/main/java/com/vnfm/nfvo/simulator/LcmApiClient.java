package com.vnfm.nfvo.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * REST client for LCM northbound API (/api/vnfs).
 */
public class LcmApiClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LcmApiClient(String baseUrl, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private String api(String path) {
        return baseUrl + "/api/vnfs" + (path.startsWith("/") ? path : "/" + path);
    }

    /**
     * POST /api/vnfs – instantiate VNF. Body: vnfType, cpuCores, memoryGb, optional requestId.
     * Header X-Request-Id for idempotency.
     */
    public ApiResponse instantiate(String vnfType, int cpu, int memory, String requestId) {
        String url = api("");
        Map<String, Object> body = Map.of(
                "vnfType", vnfType != null ? vnfType : "",
                "cpuCores", cpu,
                "memoryGb", memory
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (requestId != null && !requestId.isBlank()) {
            headers.set("X-Request-Id", requestId.trim());
        }
        Map<String, Object> bodyWithRequestId = new java.util.HashMap<>(body);
        if (requestId != null && !requestId.isBlank()) {
            bodyWithRequestId.put("requestId", requestId.trim());
        }
        HttpEntity<String> entity = new HttpEntity<>(toJson(bodyWithRequestId), headers);
        return exchange(url, HttpMethod.POST, entity, 202);
    }

    /**
     * DELETE /api/vnfs/{vnfId} – terminate VNF. Optional X-Request-Id header or requestId query.
     */
    public ApiResponse terminate(String vnfId, String requestId) {
        String url = api("/" + vnfId);
        if (requestId != null && !requestId.isBlank()) {
            url += "?requestId=" + requestId.trim();
        }
        HttpHeaders headers = new HttpHeaders();
        if (requestId != null && !requestId.isBlank()) {
            headers.set("X-Request-Id", requestId.trim());
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return exchange(url, HttpMethod.DELETE, entity, 202);
    }

    /**
     * GET /api/vnfs/{vnfId}/status – VNF status.
     */
    public ApiResponse status(String vnfId) {
        String url = api("/" + vnfId + "/status");
        return exchange(url, HttpMethod.GET, null, 200);
    }

    /**
     * GET /api/vnfs – list all VNFs.
     */
    public ApiResponse list() {
        String url = api("");
        return exchange(url, HttpMethod.GET, null, 200);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ApiResponse exchange(String url, HttpMethod method, HttpEntity<?> entity, int... expectedStatus) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity != null ? entity : HttpEntity.EMPTY, String.class);
            return new ApiResponse(response.getStatusCodeValue(), response.getHeaders(), response.getBody());
        } catch (HttpStatusCodeException e) {
            return new ApiResponse(e.getStatusCode().value(), e.getResponseHeaders(), e.getResponseBodyAsString());
        }
    }

    public static class ApiResponse {
        public final int status;
        public final HttpHeaders headers;
        public final String body;

        public ApiResponse(int status, HttpHeaders headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }
    }
}
