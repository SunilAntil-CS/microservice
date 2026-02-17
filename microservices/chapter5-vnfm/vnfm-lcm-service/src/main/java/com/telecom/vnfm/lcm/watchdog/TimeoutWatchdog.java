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

/** Finds instances stuck in DEPLOYING_INFRA > 15 min, marks FAILED (Choreography mitigation). */
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
            log.warn("Watchdog marking FAILED: vnfId={}", vnf.getVnfId());
            lcmService.markInfraFailed(vnf.getVnfId(), reason);
        }
    }
}
