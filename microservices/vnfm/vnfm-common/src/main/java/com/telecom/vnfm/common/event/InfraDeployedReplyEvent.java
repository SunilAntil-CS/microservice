package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * REPLY EVENT: Infra Deployed (VIM Adapter → LCM Service) — Phase 3 reply / Phase 4.
 * ---------------------------------------------------------------------------
 * Sent by the VIM Adapter after successfully creating the CloudDeployment and
 * marking it RUNNING. LCM consumes this on the reply topic and calls
 * lcmService.markInfraDeployed(vnfId, deploymentId) to complete the Saga
 * (Phase 5: update VnfInstance to ACTIVE).
 *
 * CONTRACT: vnfId and deploymentId are the correlation identifiers; status
 * indicates outcome (SUCCESS or failure in a full implementation).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeployedReplyEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    /** Identity of the VNF in the LCM service (correlates to VnfInstance). */
    private String vnfId;

    /** Identity of the CloudDeployment in the VIM service (stored on VnfInstance). */
    private String deploymentId;

    /** Outcome: SUCCESS, FAILED, etc. */
    private String status;

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
}
