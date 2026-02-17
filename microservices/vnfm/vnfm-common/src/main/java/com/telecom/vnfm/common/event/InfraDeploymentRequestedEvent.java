package com.telecom.vnfm.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DOMAIN EVENT: Infra Deployment Requested (LCM → VIM Adapter).
 * ---------------------------------------------------------------------------
 * DDD ROLE: This is the contract between the LCM Service (Boss) and the VIM
 * Adapter Service (Worker). It is published by LCM when a VnfInstance requests
 * infrastructure (e.g. Kubernetes pods on Cisco UCS). VIM consumes it from
 * Kafka and creates a CloudDeployment aggregate.
 *
 * WHY A SHARED EVENT DTO?
 * - Strict aggregate boundaries: LCM owns VnfInstance, VIM owns CloudDeployment.
 * - They share no database; the only coupling is this event schema (and the
 *   topic name). Reference by identity: the event carries vnfId (String); VIM
 *   never holds a reference to the VnfInstance entity, only the id.
 *
 * SERIALIZATION: This class is serialized to JSON by the outbox/Eventuate Tram
 * and deserialized by the VIM consumer. All fields must be serialization-friendly
 * (primitives, String, nested POJOs that are also serializable). No JPA or
 * framework-specific types.
 *
 * INTERVIEW TIP: In production you might use a schema registry (e.g. Avro) for
 * backward-compatible evolution; for interview code, a stable POJO is sufficient.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraDeploymentRequestedEvent implements Serializable, DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Identity of the VNF instance in the LCM service (Aggregate 1).
     * VIM stores this as a foreign key reference only—never loads VnfInstance.
     */
    private String vnfId;

    /** vCPU count required for this VNF (e.g. 8 for a 5G UPF). */
    private int vcpu;

    /** Memory in MB (e.g. 16384 for 16 GB). */
    private int memoryMb;

    /** Software version / image tag for the VNF (e.g. "v2.1.0"). */
    private String softwareVersion;
}
