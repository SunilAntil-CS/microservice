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

/**
 * LCM APPLICATION SERVICE - Single transaction boundary for aggregate + outbox.
 * ---------------------------------------------------------------------------
 * DDD ROLE: The application service does not contain business logic. It
 * coordinates the use case: (1) ask the aggregate to perform the operation,
 * (2) persist the aggregate, (3) publish the resulting domain events in the
 * same transaction via the Transactional Outbox. All business rules (e.g.
 * "requesting deployment produces InfraDeploymentRequestedEvent") live in
 * the aggregate (VnfInstance.requestDeployment).
 *
 * TRANSACTION BOUNDARY:
 * - Every method that changes state runs in a single @Transactional. So
 *   repository.save(vnf) and event publishers commit together. No Kafka
 *   call inside the TX—outbox only. The OutboxRelay (scheduled) publishes
 *   to Kafka and hard-deletes the row after broker ACK.
 *
 * NFVO NOTIFICATION:
 * - On every state change we publish VnfStatusUpdatedEvent to the outbox
 *   (destination nfvo-vnf-notifications) so the NFVO dashboard stays in sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LcmService {

    private final VnfInstanceRepository vnfInstanceRepository;
    private final DomainEventPublisher<InfraDeploymentRequestedEvent> eventPublisher;
    private final VnfStatusUpdatedPublisher vnfStatusUpdatedPublisher;

    /**
     * Instantiates a VNF: creates the VnfInstance aggregate in DEPLOYING_INFRA
     * state and publishes InfraDeploymentRequestedEvent to the outbox. Also
     * publishes VnfStatusUpdatedEvent (state DEPLOYING_INFRA) for the NFVO.
     */
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

    /**
     * Called when the VIM Adapter sends InfraDeployedReplyEvent (success).
     * Updates the aggregate to ACTIVE and notifies the NFVO.
     */
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

    /**
     * COMPENSATION: Called when the VIM Adapter sends InfraDeploymentFailedEvent
     * or when the Timeout Watchdog detects a stuck instance. Marks the VNF as
     * FAILED and notifies the NFVO. Rollback path of the Distributed Saga.
     */
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
