package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** VIM → LCM: Event 1 — ACK (VIM received the payload). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentAcceptedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private String deploymentId;
    private String message;
}
