package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** LCM → VIM: Request infrastructure deployment for a VNF. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentRequestedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private int vcpu;
    private int memoryMb;
    private String softwareVersion;
}
