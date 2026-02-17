package com.telecom.vnfm.lcm.service;

import com.telecom.vnfm.common.event.InfraDeploymentRequestedEvent;
import com.telecom.vnfm.common.event.VnfStatusUpdatedEvent;
import com.telecom.vnfm.lcm.domain.VnfInstance;
import com.telecom.vnfm.lcm.domain.VnfInstanceRepository;
import com.telecom.vnfm.lcm.domain.VnfProfile;
import com.telecom.vnfm.lcm.outbox.DomainEventPublisher;
import com.telecom.vnfm.lcm.outbox.VnfStatusUpdatedPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LcmService {

    private final VnfInstanceRepository vnfInstanceRepository;
    private final DomainEventPublisher<InfraDeploymentRequestedEvent> eventPublisher;
    private final VnfStatusUpdatedPublisher vnfStatusUpdatedPublisher;

    @Transactional(rollbackFor = Exception.class)
    public VnfInstance instantiateVnf(String vnfId, VnfProfile profile) {
        var result = VnfInstance.requestDeployment(vnfId, profile);
        VnfInstance vnf = vnfInstanceRepository.save(result.getResult());
        eventPublisher.publish(VnfInstance.class, vnfId, result.getEvents());
        vnfStatusUpdatedPublisher.publish(VnfStatusUpdatedEvent.builder()
                .vnfId(vnfId)
                .state(vnf.getState().name())
                .message("Infrastructure deployment requested")
                .build());
        log.info("VNF instantiation requested: vnfId={}, state={}", vnfId, vnf.getState());
        return vnf;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markInfraDeployed(String vnfId, String deploymentId) {
        VnfInstance vnf = vnfInstanceRepository.findById(vnfId)
                .orElseThrow(() -> new IllegalArgumentException("VnfInstance not found: " + vnfId));
        vnf.markInfraDeployed(deploymentId);
        vnfInstanceRepository.save(vnf);
        vnfStatusUpdatedPublisher.publish(VnfStatusUpdatedEvent.builder()
                .vnfId(vnfId)
                .state(VnfInstance.VnfState.ACTIVE.name())
                .deploymentId(deploymentId)
                .message("VNF is active")
                .build());
        log.info("VNF marked active: vnfId={}, deploymentId={}", vnfId, deploymentId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markInfraFailed(String vnfId, String reason) {
        VnfInstance vnf = vnfInstanceRepository.findById(vnfId)
                .orElseThrow(() -> new IllegalArgumentException("VnfInstance not found: " + vnfId));
        vnf.markInfraFailed(reason);
        vnfInstanceRepository.save(vnf);
        vnfStatusUpdatedPublisher.publish(VnfStatusUpdatedEvent.builder()
                .vnfId(vnfId)
                .state(VnfInstance.VnfState.FAILED.name())
                .message(reason)
                .build());
        log.warn("VNF marked failed: vnfId={}, reason={}", vnfId, reason);
    }
}
