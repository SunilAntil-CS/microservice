package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** VIM → LCM: Event 2 — Progress (e.g. "VM booting 50%"). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentProgressEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private String deploymentId;
    private String progressMessage;
    private Integer progressPercent;
}
