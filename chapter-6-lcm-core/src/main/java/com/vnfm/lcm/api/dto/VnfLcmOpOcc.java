package com.vnfm.lcm.api.dto;

import java.time.Instant;

/**
 * ETSI SOL002/003 VNF LCM Operation Occurrence for GET /vnflcm/v1/vnf_lcm_op_occs/{opId}.
 */
public class VnfLcmOpOcc {

    private String id;
    private String operation;       // INSTANTIATE, TERMINATE, etc.
    private String state;           // STARTING, PROCESSING, COMPLETED, FAILED, ROLLING_BACK
    private String vnfInstanceId;
    private Instant startTime;
    private Instant endTime;
    private ProblemDetails error;

    public VnfLcmOpOcc() {
    }

    public VnfLcmOpOcc(String id, String operation, String state, String vnfInstanceId,
                      Instant startTime, Instant endTime, ProblemDetails error) {
        this.id = id;
        this.operation = operation;
        this.state = state;
        this.vnfInstanceId = vnfInstanceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.error = error;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getVnfInstanceId() {
        return vnfInstanceId;
    }

    public void setVnfInstanceId(String vnfInstanceId) {
        this.vnfInstanceId = vnfInstanceId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public ProblemDetails getError() {
        return error;
    }

    public void setError(ProblemDetails error) {
        this.error = error;
    }

    /** ETSI-style problem details for failed operations. */
    public static class ProblemDetails {
        private String detail;
        private String title;
        private int status;

        public ProblemDetails() {
        }

        public ProblemDetails(String detail, String title, int status) {
            this.detail = detail;
            this.title = title;
            this.status = status;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }
    }
}
