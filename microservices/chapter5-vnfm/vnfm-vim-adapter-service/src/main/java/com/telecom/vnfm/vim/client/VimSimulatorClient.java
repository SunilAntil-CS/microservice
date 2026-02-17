package com.telecom.vnfm.vim.client;

import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VimSimulatorClient {

    private final RestTemplate restTemplate;

    @Value("${vnfm.vim.simulator.url:http://localhost:8082}")
    private String simulatorBaseUrl;
    @Value("${vnfm.vim.callback-base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    @Async
    public void deployAsync(InfraDeploymentRequestedEvent event, String deploymentId) {
        String url = simulatorBaseUrl + "/api/openstack/deploy";
        String callbackUrl = callbackBaseUrl + "/api/vim/callback";
        try {
            Map<String, Object> body = Map.of(
                    "vnfId", event.getVnfId(),
                    "deploymentId", deploymentId != null ? deploymentId : "",
                    "vcpu", event.getVcpu(),
                    "memoryMb", event.getMemoryMb(),
                    "softwareVersion", event.getSoftwareVersion() != null ? event.getSoftwareVersion() : "",
                    "callbackUrl", callbackUrl
            );
            restTemplate.postForObject(url, body, String.class);
            log.info("Simulator deploy requested: vnfId={}, deploymentId={}", event.getVnfId(), deploymentId);
        } catch (Exception e) {
            log.error("Simulator deploy failed: vnfId={}", event.getVnfId(), e);
        }
    }
}
