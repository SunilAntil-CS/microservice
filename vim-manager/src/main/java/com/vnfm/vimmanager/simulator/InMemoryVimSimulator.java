package com.vnfm.vimmanager.simulator;

import com.vnfm.vimmanager.client.VimRequest;
import com.vnfm.vimmanager.client.VimResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * In-memory VIM simulator. Can be configured to succeed or fail for testing.
 */
@Component("inMemoryVimSimulator")
@ConditionalOnProperty(name = "vim.simulator.enabled", havingValue = "true", matchIfMissing = true)
public class InMemoryVimSimulator implements com.vnfm.vimmanager.client.VimClient {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVimSimulator.class);

    private final VimSimulatorProperties properties;

    public InMemoryVimSimulator(VimSimulatorProperties properties) {
        this.properties = properties;
    }

    @Override
    public VimResponse execute(VimRequest request) {
        log.debug("Simulator executing {} sagaId={} vnfId={}", request.getCommandType(), request.getSagaId(), request.getVnfId());
        if (properties.isSucceed()) {
            Map<String, Object> result = Map.of(
                    "reservationId", "res-" + request.getSagaId(),
                    "vnfId", request.getVnfId()
            );
            return VimResponse.ok(result);
        } else {
            return VimResponse.fail("Simulator configured to fail");
        }
    }
}
