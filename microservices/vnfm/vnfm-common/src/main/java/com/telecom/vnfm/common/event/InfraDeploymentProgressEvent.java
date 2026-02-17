package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * VIM → LCM: Event 2 of the 3-Event Lifecycle — Progress (e.g. "VM booting 50%").
 * ---------------------------------------------------------------------------
 * Sent by the VIM Adapter when the physical infrastructure reports progress
 * (e.g. via webhook from OpenStack). LCM may log or expose this to the operator.
 * Not terminal; the saga completes on InfraDeployedReplyEvent or InfraDeploymentFailedEvent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentProgressEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private String deploymentId;

    /** Progress percentage 0–100 or status text (e.g. "VM booting 50%"). */
    private String progressMessage;

    private Integer progressPercent;
}
