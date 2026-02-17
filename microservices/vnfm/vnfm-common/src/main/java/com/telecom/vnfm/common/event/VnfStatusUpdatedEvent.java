package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * LCM → NFVO: VNF lifecycle state change notification.
 * ---------------------------------------------------------------------------
 * Published by the LCM service whenever a VnfInstance's state changes
 * (e.g. INSTANTIATING → DEPLOYING_INFRA → ACTIVE, or → FAILED). The NFVO
 * (orchestrator) consumes this from topic {@code nfvo-vnf-notifications} to
 * update its dashboard and enforce policy. ETSI MANO: this is the VNF lifecycle
 * state report from VNFM to NFVO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VnfStatusUpdatedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;

    /** Current state: INSTANTIATING, DEPLOYING_INFRA, ACTIVE, FAILED. */
    private String state;

    /** Optional: deployment id when ACTIVE; failure reason when FAILED. */
    private String deploymentId;
    private String message;
}
