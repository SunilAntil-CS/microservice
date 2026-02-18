package com.vnfm.vimmanager.client;

import java.util.Map;

/**
 * Response from VIM (success or failure).
 */
public class VimResponse {

    private final boolean success;
    private final String errorMessage;
    private final Map<String, Object> result;

    public VimResponse(boolean success, String errorMessage, Map<String, Object> result) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.result = result;
    }

    public static VimResponse ok(Map<String, Object> result) {
        return new VimResponse(true, null, result);
    }

    public static VimResponse fail(String errorMessage) {
        return new VimResponse(false, errorMessage, null);
    }

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, Object> getResult() { return result; }
}
