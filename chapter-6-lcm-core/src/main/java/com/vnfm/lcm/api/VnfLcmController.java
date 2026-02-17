package com.vnfm.lcm.api;

import com.vnfm.lcm.api.dto.CreateVnfInstanceRequest;
import com.vnfm.lcm.api.dto.InstantiateVnfRequestLcm;
import com.vnfm.lcm.api.dto.VnfInstance;
import com.vnfm.lcm.api.dto.VnfLcmOpOcc;
import com.vnfm.lcm.application.VnfLcmApplicationService;
import com.vnfm.lcm.application.VnfQueryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ETSI SOL002/003 compliant northbound API for VNF LCM.
 * Base path: /vnflcm/v1
 * - POST /vnf_instances -> 201 Created with Location
 * - POST /vnf_instances/{vnfId}/instantiate -> 202 Accepted with Location to operation occurrence
 * - GET /vnf_instances/{vnfId} -> VnfInstance
 * - GET /vnf_instances -> List of VnfInstance
 * - GET /vnf_lcm_op_occs/{opId} -> VnfLcmOpOcc
 */
@RestController
@RequestMapping("/vnflcm/v1")
public class VnfLcmController {

    private static final String BASE = "/vnflcm/v1";

    private final VnfLcmApplicationService applicationService;
    private final VnfQueryService queryService;

    public VnfLcmController(VnfLcmApplicationService applicationService, VnfQueryService queryService) {
        this.applicationService = applicationService;
        this.queryService = queryService;
    }

    @PostMapping(value = "/vnf_instances", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VnfInstance> createVnfInstance(@RequestBody(required = false) CreateVnfInstanceRequest request) {
        VnfInstance created = applicationService.createVnfInstance(request != null ? request : new CreateVnfInstanceRequest());
        return ResponseEntity
                .status(201)
                .location(java.net.URI.create(BASE + "/vnf_instances/" + created.getId()))
                .body(created);
    }

    @PostMapping(value = "/vnf_instances/{vnfId}/instantiate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> instantiateVnf(
            @PathVariable String vnfId,
            @RequestBody(required = false) InstantiateVnfRequestLcm request,
            @RequestHeader(value = "X-Request-ID", required = false) String requestId) {
        java.util.UUID opId = applicationService.startInstantiation(vnfId, request != null ? request : new InstantiateVnfRequestLcm());
        String location = BASE + "/vnf_lcm_op_occs/" + opId;
        return ResponseEntity
                .status(202)
                .location(java.net.URI.create(location))
                .build();
    }

    @GetMapping(value = "/vnf_instances/{vnfId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VnfInstance> getVnfInstance(@PathVariable String vnfId) {
        if (!queryService.exists(vnfId)) {
            return ResponseEntity.notFound().build();
        }
        VnfInstance instance = queryService.getVnfInstance(vnfId);
        return ResponseEntity.ok(instance);
    }

    @GetMapping(value = "/vnf_instances", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<VnfInstance>> listVnfInstances() {
        return ResponseEntity.ok(queryService.listVnfInstances());
    }

    @GetMapping(value = "/vnf_lcm_op_occs/{opId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VnfLcmOpOcc> getOperationOccurrence(@PathVariable String opId) {
        VnfLcmOpOcc opOcc = queryService.getOperationOccurrence(opId);
        if (opOcc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(opOcc);
    }
}
