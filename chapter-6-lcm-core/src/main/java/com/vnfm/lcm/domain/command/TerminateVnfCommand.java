package com.vnfm.lcm.domain.command;

import com.vnfm.lcm.domain.Command;

/**
 * Command to terminate an existing VNF.
 * The requestId supports idempotent handling of duplicate requests.
 */
public record TerminateVnfCommand(
        String vnfId,
        String requestId
) implements Command {
}
