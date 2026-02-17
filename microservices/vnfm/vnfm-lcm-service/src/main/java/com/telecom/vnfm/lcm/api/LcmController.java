package com.telecom.vnfm.lcm.api;

import com.telecom.vnfm.lcm.domain.VnfProfile;
import com.telecom.vnfm.lcm.service.LcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * REST API for LCM operations. Triggers VNF instantiation (aggregate + outbox in one TX).
 */
@RestController
@RequestMapping("/api/v1/vnf")
@RequiredArgsConstructor
public class LcmController {

    private final LcmService lcmService;

    @PostMapping("/instantiate")
    public ResponseEntity<?> instantiate(
            @Valid @RequestBody InstantiateRequest request) {
        VnfProfile profile = VnfProfile.of(
                request.getVcpu(),
                request.getMemoryMb(),
                request.getSoftwareVersion()
        );
        var vnf = lcmService.instantiateVnf(request.getVnfId(), profile);
        return ResponseEntity.status(201).body(Map.of(
                "vnfId", vnf.getVnfId(),
                "state", vnf.getState().name()
        ));
    }

    @lombok.Data
    public static class InstantiateRequest {
        @NotBlank
        private String vnfId;
        @NotNull
        private Integer vcpu;
        @NotNull
        private Integer memoryMb;
        private String softwareVersion;
    }
}
