package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** VIM → LCM: Event 3 — Terminal Success. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeployedReplyEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    private String vnfId;
    private String deploymentId;
    private String status;

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
}
