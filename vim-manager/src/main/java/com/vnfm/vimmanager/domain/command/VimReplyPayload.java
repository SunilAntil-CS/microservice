package com.vnfm.vimmanager.domain.command;

import java.util.Map;

/**
 * Reply sent back to LCM on vim.replies: success/failure and optional result.
 */
public class VimReplyPayload {

    private String sagaId;
    private int step;
    private boolean success;
    private String reason;
    private Map<String, Object> result;

    public static VimReplyPayload success(String sagaId, int step, Map<String, Object> result) {
        VimReplyPayload p = new VimReplyPayload();
        p.setSagaId(sagaId);
        p.setStep(step);
        p.setSuccess(true);
        p.setResult(result);
        return p;
    }

    public static VimReplyPayload failure(String sagaId, int step, String reason) {
        VimReplyPayload p = new VimReplyPayload();
        p.setSagaId(sagaId);
        p.setStep(step);
        p.setSuccess(false);
        p.setReason(reason);
        return p;
    }

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
