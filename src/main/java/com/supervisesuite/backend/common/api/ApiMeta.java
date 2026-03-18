package com.supervisesuite.backend.common.api;

import java.time.Instant;

public class ApiMeta {

    private Instant timestamp;
    private String path;
    private String traceId;

    public ApiMeta() {
    }

    public ApiMeta(Instant timestamp, String path, String traceId) {
        this.timestamp = timestamp;
        this.path = path;
        this.traceId = traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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
}
