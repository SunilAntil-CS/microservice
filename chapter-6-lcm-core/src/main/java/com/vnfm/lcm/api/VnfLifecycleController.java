package com.vnfm.lcm.api;

import com.vnfm.lcm.api.dto.InstantiateRequest;
import com.vnfm.lcm.api.dto.InstantiateResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Northbound REST API for VNF lifecycle operations.
 * All operations under /api/ are subject to idempotency (IdempotencyFilter):
 * duplicate requestId returns cached response without re-executing.
 */
@RestController
@RequestMapping("/api/v1/vnf")
public class VnfLifecycleController {

    /**
     * Instantiate VNF – accepts requestId (in body or via X-Request-Id header) and returns a response.
     * First call with a given requestId is processed and the response is cached; subsequent calls
     * with the same requestId return the cached response (handled by IdempotencyFilter).
     */
    @PostMapping(value = "/instantiate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InstantiateResponse> instantiate(@RequestBody InstantiateRequest request) {
        String requestId = request != null ? request.getRequestId() : null;
        String vnfId = request != null ? request.getVnfId() : null;
        // Placeholder: real implementation will use application service and event store
        InstantiateResponse response = new InstantiateResponse(
                requestId,
                vnfId,
                "ACCEPTED",
                "Instantiation request accepted (idempotency key: " + requestId + ")"
        );
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Optional: GET by requestId for clients that prefer to pass idempotency key in query.
     * IdempotencyFilter still applies when X-Request-Id header is set on POST; this method
     * is for convenience and can return the same shape of response.
     */
    @GetMapping(value = "/instantiate/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InstantiateResponse> getInstantiateStatus(@PathVariable String requestId) {
        // Placeholder: in a full implementation this would look up operation status
        InstantiateResponse response = new InstantiateResponse(
                requestId,
                null,
                "UNKNOWN",
                "Status for requestId: " + requestId
        );
        return ResponseEntity.ok().body(response);
    }
}
