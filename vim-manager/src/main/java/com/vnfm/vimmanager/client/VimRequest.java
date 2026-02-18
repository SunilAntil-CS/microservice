package com.vnfm.vimmanager.client;

import java.util.Map;

/**
 * VIM-specific request (for now pass-through from generic payload).
 */
public class VimRequest {

    private String commandType;
    private String sagaId;
    private String vnfId;
    private Map<String, Object> body;

    public VimRequest(String commandType, String sagaId, String vnfId, Map<String, Object> body) {
        this.commandType = commandType;
        this.sagaId = sagaId;
        this.vnfId = vnfId;
        this.body = body;
    }

    public String getCommandType() { return commandType; }
    public String getSagaId() { return sagaId; }
    public String getVnfId() { return vnfId; }
    public Map<String, Object> getBody() { return body; }
}
