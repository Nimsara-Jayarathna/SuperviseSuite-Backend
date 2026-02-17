package com.supervisesuite.backend.common.error;

public class ApiErrorDetail {
    private String field;
    private String issue;

    public ApiErrorDetail() {
    }

    public ApiErrorDetail(String field, String issue) {
        this.field = field;
        this.issue = issue;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }
}
