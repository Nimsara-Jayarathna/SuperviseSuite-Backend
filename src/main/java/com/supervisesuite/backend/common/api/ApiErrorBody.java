package com.supervisesuite.backend.common.api;

import com.supervisesuite.backend.common.error.ApiErrorDetail;
import java.util.ArrayList;
import java.util.List;

public class ApiErrorBody {

    private String code;
    private int status;
    private List<ApiErrorDetail> details = new ArrayList<>();

    public ApiErrorBody() {
    }

    public ApiErrorBody(String code, int status, List<ApiErrorDetail> details) {
        this.code = code;
        this.status = status;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public List<ApiErrorDetail> getDetails() {
        return details;
    }

    public void setDetails(List<ApiErrorDetail> details) {
        this.details = details == null ? List.of() : List.copyOf(details);
    }
}
