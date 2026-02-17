package com.telecom.vnfm.simulator.api;

import com.telecom.vnfm.simulator.service.DeployWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        deployWebhookService.runAsyncWebhooks(request);
        return ResponseEntity.accepted().body(Map.of("status", "ACCEPTED", "message", "Deployment queued"));
    }
}
