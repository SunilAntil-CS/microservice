package com.telecom.vnfm.lcm.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.Objects;

/**
 * VALUE OBJECT: VNF resource and software profile (CPU, Memory, Version).
 * ---------------------------------------------------------------------------
 * DDD ROLE: In "Microservices Patterns" (Richardson), Value Objects have no
 * identity—they are defined entirely by their attributes. Two VnfProfiles
 * with the same vcpu, memoryMb, and softwareVersion are interchangeable.
 * We use @Embeddable so JPA stores these columns in the same table as the
 * owning entity (VnfInstance), avoiding a separate table and preserving
 * the invariant that a profile cannot exist without a VNF instance.
 *
 * WHY @Embeddable INSTEAD OF @Entity?
 * - No lifecycle of its own: we never load/update a VnfProfile by id.
 * - Encapsulation: the aggregate (VnfInstance) owns the profile; no other
 *   entity should reference VnfProfile by id.
 * - Transaction boundary: the profile is always persisted in the same
 *   transaction as the aggregate root. No lazy loading across tables.
 *
 * WHY NO PUBLIC SETTERS?
 * - Value Objects are typically immutable. Once created (via constructor or
 *   static factory), the state does not change. This prevents accidental
 *   modification and makes the aggregate easier to reason about. If the
 *   business requires a "new" profile, we create a new instance.
 *
 * PRODUCTION NOTE: For a 15M-subscriber VNFM, this might be extended with
 * storage requirements, NIC counts, or affinity rules—all as part of the
 * same value object to keep the aggregate boundary clear.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VnfProfile {

    /**
     * Virtual CPU count (e.g. 8 for a medium UPF). Stored in the vnf_instances
     * table as profile_vcpu due to @Embeddable column naming.
     */
    @Column(name = "profile_vcpu", nullable = false)
    private int vcpu;

    /**
     * Memory in MB (e.g. 16384 for 16 GB). Kept as int to avoid floating
     * point and to align with common infra APIs (Kubernetes resource requests).
     */
    @Column(name = "profile_memory_mb", nullable = false)
    private int memoryMb;

    /**
     * Software version or image tag (e.g. "v2.1.0"). Used by the VIM to
     * pull the correct container image or VM template.
     */
    @Column(name = "profile_software_version", length = 64)
    private String softwareVersion;

    /**
     * Static factory: creates a VnfProfile with the given requirements.
     * We use a factory instead of a public constructor to validate invariants
     * in one place (e.g. vcpu > 0, memoryMb > 0) and to document creation.
     */
    public static VnfProfile of(int vcpu, int memoryMb, String softwareVersion) {
        if (vcpu <= 0 || memoryMb <= 0) {
            throw new IllegalArgumentException("vcpu and memoryMb must be positive");
        }
        return new VnfProfile(vcpu, memoryMb, softwareVersion != null ? softwareVersion : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VnfProfile that = (VnfProfile) o;
        return vcpu == that.vcpu && memoryMb == that.memoryMb
                && Objects.equals(softwareVersion, that.softwareVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vcpu, memoryMb, softwareVersion);
    }
}
