package com.telecom.vnfm.simulator.service;

import com.telecom.vnfm.simulator.api.DeployRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployWebhookService {

    private final RestTemplate restTemplate;
    private final Random random = new Random();

    @Async
    public void runAsyncWebhooks(DeployRequest request) {
        String callbackUrl = request.getCallbackUrl();
        String vnfId = request.getVnfId();
        String deploymentId = request.getDeploymentId();
        try {
            Thread.sleep(2000);
            postProgress(callbackUrl, vnfId, deploymentId);
            Thread.sleep(3000);
            if (random.nextBoolean()) {
                postSuccess(callbackUrl, vnfId, deploymentId);
            } else {
                postFailure(callbackUrl, vnfId, deploymentId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Webhook thread interrupted", e);
        }
    }

    private void postProgress(String callbackUrl, String vnfId, String deploymentId) {
        Map<String, Object> body = Map.of(
                "vnfId", vnfId != null ? vnfId : "",
                "deploymentId", deploymentId != null ? deploymentId : "",
                "type", "PROGRESS",
                "progressMessage", "VM booting 50%",
                "progressPercent", 50
        );
        try {
            restTemplate.postForObject(callbackUrl, body, String.class);
        } catch (Exception e) {
            log.error("Progress webhook failed: {}", e.getMessage());
        }
    }

    private void postSuccess(String callbackUrl, String vnfId, String deploymentId) {
        Map<String, Object> body = Map.of(
                "vnfId", vnfId != null ? vnfId : "",
                "deploymentId", deploymentId != null ? deploymentId : "",
                "type", "SUCCESS"
        );
        try {
            restTemplate.postForObject(callbackUrl, body, String.class);
        } catch (Exception e) {
            log.error("Success webhook failed: {}", e.getMessage());
        }
    }

    private void postFailure(String callbackUrl, String vnfId, String deploymentId) {
        Map<String, Object> body = Map.of(
                "vnfId", vnfId != null ? vnfId : "",
                "deploymentId", deploymentId != null ? deploymentId : "",
                "type", "FAILURE",
                "reason", "Simulated failure",
                "errorCode", "SIM_FAIL"
        );
        try {
            restTemplate.postForObject(callbackUrl, body, String.class);
        } catch (Exception e) {
            log.error("Failure webhook failed: {}", e.getMessage());
        }
    }
}
