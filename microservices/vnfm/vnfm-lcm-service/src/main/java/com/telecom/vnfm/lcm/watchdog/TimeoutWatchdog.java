package com.telecom.vnfm.lcm.watchdog;

import com.telecom.vnfm.lcm.domain.VnfInstance;
import com.telecom.vnfm.lcm.domain.VnfInstanceRepository;
import com.telecom.vnfm.lcm.service.LcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * TIMEOUT WATCHDOG: Mitigates the "Lost in Space" flaw of Choreography.
 * ---------------------------------------------------------------------------
 * In a Pure Choreography saga there is no central orchestrator. If the VIM
 * Adapter crashes after consuming InfraDeploymentRequestedEvent but before
 * sending InfraDeployedReplyEvent, the LCM has no way to know—the VnfInstance
 * would stay in DEPLOYING_INFRA forever ("lost in space"). This scheduled job
 * finds instances stuck in DEPLOYING_INFRA for longer than the configured
 * timeout (default 15 minutes) and marks them FAILED, emitting VnfStatusUpdatedEvent
 * to the NFVO so the operator can take corrective action.
 *
 * WHY NOT USE AN ORCHESTRATOR? For a 2-actor system (LCM and VIM), introducing
 * Temporal or Camunda would be over-engineering. Choreography + Watchdog gives
 * us decoupling and reliability without the operational cost of a central engine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutWatchdog {

    private final VnfInstanceRepository vnfInstanceRepository;
    private final LcmService lcmService;

    @Value("${vnfm.lcm.watchdog.timeout-minutes:15}")
    private int timeoutMinutes;

    @Value("${vnfm.lcm.watchdog.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${vnfm.lcm.watchdog.interval-ms:60000}")
    public void markStuckInstancesFailed() {
        if (!enabled) return;

        Instant cutoff = Instant.now().minusSeconds(timeoutMinutes * 60L);
        List<VnfInstance> stuck = vnfInstanceRepository.findByStateAndUpdatedAtBefore(
                VnfInstance.VnfState.DEPLOYING_INFRA, cutoff);

        for (VnfInstance vnf : stuck) {
            String reason = "Timeout: no response from VIM within " + timeoutMinutes + " minutes";
            log.warn("Watchdog marking stuck VNF as FAILED: vnfId={}, reason={}", vnf.getVnfId(), reason);
            lcmService.markInfraFailed(vnf.getVnfId(), reason);
        }
    }
}
