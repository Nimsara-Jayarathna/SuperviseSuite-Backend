package com.supervisesuite.backend.common.error;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ApiError {
    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private String traceId;
    private List<ApiErrorDetail> details = new ArrayList<>();

    public ApiError() {
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<ApiErrorDetail> getDetails() {
        return details;
    }

    public void setDetails(List<ApiErrorDetail> details) {
        this.details = details;
    }
}
