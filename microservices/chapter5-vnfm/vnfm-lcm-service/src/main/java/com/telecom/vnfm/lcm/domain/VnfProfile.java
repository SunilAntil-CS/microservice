package com.telecom.vnfm.lcm.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VnfProfile {

    @Column(name = "profile_vcpu", nullable = false)
    private int vcpu;

    @Column(name = "profile_memory_mb", nullable = false)
    private int memoryMb;

    @Column(name = "profile_software_version", length = 64)
    private String softwareVersion;

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
