package com.telecom.vnfm.simulator.api;

import com.telecom.vnfm.simulator.service.DeployWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Mock OpenStack deploy endpoint.
 * ---------------------------------------------------------------------------
 * Returns 202 Accepted immediately. Spins up an async flow that:
 * 1. Sleeps 2 seconds, then POSTs a "Progress" webhook to the VIM Adapter.
 * 2. Sleeps 3 seconds, then randomly POSTs "Success" or "Failure" to simulate
 *    real-world unreliability.
 */
@Slf4j
@RestController
@RequestMapping("/api/openstack")
@RequiredArgsConstructor
public class OpenStackDeployController {

    private final DeployWebhookService deployWebhookService;

    @PostMapping("/deploy")
    public ResponseEntity<Map<String, String>> deploy(@RequestBody DeployRequest request) {
        if (request == null || request.getCallbackUrl() == null || request.getCallbackUrl().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Deploy requested: vnfId={}, deploymentId={}, callbackUrl={}",
                request.getVnfId(), request.getDeploymentId(), request.getCallbackUrl());

        deployWebhookService.runAsyncWebhooks(request);

        return ResponseEntity.accepted()
                .body(Map.of("status", "ACCEPTED", "message", "Deployment queued"));
    }
}
