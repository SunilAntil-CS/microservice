package com.vnfm.lcm.infrastructure.vim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Reply from VIM Manager on vim.replies (success/failure and optional result).
 * Matches the payload sent by vim-manager's VimReplyPayload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VimReplyPayload {

    private String sagaId;
    private int step;
    private boolean success;
    private String reason;
    private Map<String, Object> result;

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
}
