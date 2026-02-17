package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * VIM → LCM: Terminal Failure in the 3-Event Lifecycle.
 * ---------------------------------------------------------------------------
 * Sent by the VIM Adapter when the physical infrastructure fails (e.g. quota
 * exceeded, image not found). LCM must execute compensation: mark the VnfInstance
 * as FAILED and emit VnfStatusUpdatedEvent to the NFVO so the operator can
 * take corrective action. This is the rollback path of the Distributed Saga.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentFailedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private String deploymentId;

    /** Failure reason (e.g. "Quota exceeded", "Image not found"). */
    private String reason;

    /** Optional error code from the VIM. */
    private String errorCode;
}
