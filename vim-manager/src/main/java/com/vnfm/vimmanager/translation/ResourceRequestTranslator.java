package com.vnfm.vimmanager.translation;

import com.vnfm.vimmanager.client.VimRequest;
import com.vnfm.vimmanager.domain.command.VimCommandPayload;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts generic resource request to VIM-specific format.
 * For now: pass-through (body = payload as map).
 */
@Component
public class ResourceRequestTranslator {

    public VimRequest toVimRequest(String commandType, VimCommandPayload payload) {
        Map<String, Object> body = new HashMap<>();
        if (payload.getSagaId() != null) body.put("sagaId", payload.getSagaId());
        if (payload.getVnfId() != null) body.put("vnfId", payload.getVnfId());
        if (payload.getOperationId() != null) body.put("operationId", payload.getOperationId());
        if (payload.getResources() != null) body.put("resources", payload.getResources());
        if (payload.getReason() != null) body.put("reason", payload.getReason());
        return new VimRequest(commandType, payload.getSagaId(), payload.getVnfId(), body);
    }
}
