package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** LCM → NFVO: VNF lifecycle state change (topic nfvo-vnf-notifications). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VnfStatusUpdatedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private String state;
    private String deploymentId;
    private String message;
}
