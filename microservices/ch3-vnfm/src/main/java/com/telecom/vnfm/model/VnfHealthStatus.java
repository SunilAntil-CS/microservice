package com.telecom.vnfm.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Response DTO for GET /vnf_instances/{id}/health. Same JSON shape as NFVO's
 * VnfHealthStatus so the consumer can deserialise and we keep a single API contract.
 * ------------------------------------------------------------------------------------
 * JAVA RECORD: Immutable carrier for vnfId, status, message. Compiler generates
 * constructor, getters, equals, hashCode, toString. Compact constructor validates
 * non-null. (See NFVO's VnfHealthStatus.java for full record/Jackson notes.)
 * ------------------------------------------------------------------------------------
 * @JsonInclude(JsonInclude.Include.NON_NULL): Jackson annotation—omit fields that
 * are null when serialising to JSON. So "message" is only present when we set it
 * (e.g. for "VNF not registered"). Same as in NFVO model.
 * ------------------------------------------------------------------------------------
 * java.util.Objects.requireNonNull: JDK built-in; throws NPE with message if null.
 * Used in compact constructor for fail-fast validation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VnfHealthStatus(
        String vnfId,
        String status,
        String message
) {
    public VnfHealthStatus {
        Objects.requireNonNull(vnfId, "vnfId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
