package com.supervisesuite.backend.common.api;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private ApiErrorBody error;
    private ApiMeta meta;

    public ApiResponse() {
    }

    public ApiResponse(boolean success, String message, T data, ApiErrorBody error, ApiMeta meta) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.error = error;
        this.meta = meta;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ApiErrorBody getError() {
        return error;
    }

    public void setError(ApiErrorBody error) {
        this.error = error;
    }

    public ApiMeta getMeta() {
        return meta;
    }

    public void setMeta(ApiMeta meta) {
        this.meta = meta;
    }
}
