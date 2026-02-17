package com.telecom.vnfm.vim.api;

import lombok.Data;

@Data
public class VimWebhookDto {

    public static final String TYPE_PROGRESS = "PROGRESS";
    public static final String TYPE_SUCCESS = "SUCCESS";
    public static final String TYPE_FAILURE = "FAILURE";

    private String vnfId;
    private String deploymentId;
    private String type;
    private String progressMessage;
    private Integer progressPercent;
    private String reason;
    private String errorCode;
}
