package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** VIM → LCM: Terminal Failure — LCM runs compensation (mark FAILED). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentFailedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private String deploymentId;
    private String reason;
    private String errorCode;
}
