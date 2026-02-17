package com.vnfm.lcm.api;

import com.vnfm.lcm.api.dto.InstantiateVnfRequest;
import com.vnfm.lcm.api.dto.InstantiateVnfResponse;
import com.vnfm.lcm.api.dto.VnfStateResponse;
import com.vnfm.lcm.api.dto.VnfSummary;
import com.vnfm.lcm.application.VnfQueryService;
import com.vnfm.lcm.infrastructure.readside.VnfIndex;
import com.vnfm.lcm.infrastructure.readside.VnfIndexRepository;
import com.vnfm.lcm.infrastructure.saga.SagaOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Northbound REST API for VNF lifecycle. All operations under /api/ use the idempotency filter.
 * POST/DELETE trigger sagas and return 202 with a status endpoint.
 */
@RestController
@RequestMapping("/api/vnfs")
public class VnfController {

    private final SagaOrchestrator sagaOrchestrator;
    private final VnfIndexRepository vnfIndexRepository;
    private final VnfQueryService vnfQueryService;

    public VnfController(SagaOrchestrator sagaOrchestrator,
                         VnfIndexRepository vnfIndexRepository,
                         VnfQueryService vnfQueryService) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.vnfIndexRepository = vnfIndexRepository;
        this.vnfQueryService = vnfQueryService;
    }

    /**
     * Instantiate a new VNF. Creates a saga and returns 202 with Location to status endpoint.
     * Idempotency: use requestId in body or X-Request-Id header.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<InstantiateVnfResponse> instantiate(@RequestBody InstantiateVnfRequest request) {
        UUID vnfId = UUID.randomUUID();
        String vnfIdStr = vnfId.toString();

        vnfIndexRepository.save(new VnfIndex(vnfIdStr));

        Map<String, Object> resources = Map.of(
                "vnfType", request != null && request.getVnfType() != null ? request.getVnfType() : "",
                "cpuCores", request != null ? request.getCpuCores() : 0,
                "memoryGb", request != null ? request.getMemoryGb() : 0
        );
        sagaOrchestrator.startInstantiateSaga(vnfIdStr, resources);

        String statusUrl = "/api/vnfs/" + vnfIdStr + "/status";
        InstantiateVnfResponse body = new InstantiateVnfResponse(
                vnfIdStr,
                statusUrl,
                "Instantiation accepted; poll " + statusUrl + " for status."
        );
        return ResponseEntity.accepted()
                .location(ServletUriComponentsBuilder.fromCurrentContextPath().path(statusUrl).build().toUri())
                .body(body);
    }

    /**
     * Terminate a VNF. requestId via header X-Request-Id or query param for idempotency. Returns 202.
     */
    @DeleteMapping("/{vnfId}")
    @Transactional
    public ResponseEntity<Void> terminate(
            @PathVariable String vnfId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestIdHeader,
            @RequestParam(value = "requestId", required = false) String requestIdParam) {
        sagaOrchestrator.startTerminateSaga(vnfId);
        return ResponseEntity.accepted().build();
    }

    /**
     * Get current VNF state (projected from event store). Returns 404 if VNF is not in the index.
     */
    @GetMapping(value = "/{vnfId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VnfStateResponse> getVnf(@PathVariable String vnfId) {
        if (!vnfQueryService.exists(vnfId)) {
            return ResponseEntity.notFound().build();
        }
        VnfStateResponse state = vnfQueryService.getVnfState(vnfId);
        return ResponseEntity.ok(state);
    }

    /**
     * Status endpoint (same as GET /api/vnfs/{vnfId}). Used as Location target after POST.
     */
    @GetMapping(value = "/{vnfId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VnfStateResponse> getVnfStatus(@PathVariable String vnfId) {
        if (!vnfQueryService.exists(vnfId)) {
            return ResponseEntity.notFound().build();
        }
        VnfStateResponse state = vnfQueryService.getVnfState(vnfId);
        return ResponseEntity.ok(state);
    }

    /**
     * List all VNFs (from read-side index; state projected from event store).
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<VnfSummary>> listVnfs() {
        List<VnfSummary> list = vnfQueryService.listVnfs();
        return ResponseEntity.ok(list);
    }
}
