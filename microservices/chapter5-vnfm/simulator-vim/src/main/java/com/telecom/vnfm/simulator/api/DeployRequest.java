package com.telecom.vnfm.simulator.api;

import lombok.Data;

@Data
public class DeployRequest {

    private String vnfId;
    private String deploymentId;
    private Integer vcpu;
    private Integer memoryMb;
    private String softwareVersion;
    private String callbackUrl;
}
