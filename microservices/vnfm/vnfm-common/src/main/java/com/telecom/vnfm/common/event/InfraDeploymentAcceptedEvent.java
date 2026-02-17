package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * VIM → LCM: Event 1 of the 3-Event Lifecycle — ACK (VIM received the payload).
 * ---------------------------------------------------------------------------
 * Sent by the VIM Adapter immediately after consuming InfraDeploymentRequestedEvent.
 * It confirms that the request was accepted and is being processed. LCM may use
 * this for observability (e.g. "request accepted") or to start a progress timer.
 * Not terminal; the saga continues with Progress and then Success/Failure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentAcceptedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    /** Identity of the VNF in the LCM service. */
    private String vnfId;

    /** Deployment id assigned by VIM (for correlation with Progress/Reply). */
    private String deploymentId;

    /** Human-readable acceptance message (e.g. "Request accepted, deployment queued"). */
    private String message;
}
