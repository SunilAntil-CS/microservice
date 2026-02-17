package com.vnfm.lcm.domain.command;

import com.vnfm.lcm.domain.Command;

/**
 * Command to instantiate a VNF with the given resource requirements.
 * The requestId supports idempotent handling of duplicate requests.
 */
public record InstantiateVnfCommand(
        String vnfId,
        String vnfType,
        int cpuCores,
        int memoryGb,
        String requestId
) implements Command {
}
