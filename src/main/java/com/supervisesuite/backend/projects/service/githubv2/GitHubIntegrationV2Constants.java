package com.supervisesuite.backend.projects.service.githubv2;

public final class GitHubIntegrationV2Constants {

    public static final String ACCESS_TYPE_PUBLIC_URL = "PUBLIC_URL";
    public static final String ACCESS_TYPE_INSTALLATION_DIRECT = "INSTALLATION_DIRECT";
    public static final String ACCESS_TYPE_INSTALLATION_REQUESTED = "INSTALLATION_REQUESTED";

    public static final String FLOW_TYPE_INSTALLATION_DIRECT = "INSTALLATION_DIRECT";
    public static final String FLOW_TYPE_INSTALLATION_REQUESTED = "INSTALLATION_REQUESTED";

    public static final String OWNER_TYPE_USER = "USER";
    public static final String OWNER_TYPE_ORG = "ORG";

    public static final String SYNC_STATUS_SUCCESS = "SUCCESS";
    public static final String SYNC_STATUS_FAILED = "FAILED";
    public static final String SYNC_STATUS_PENDING = "PENDING";

    private GitHubIntegrationV2Constants() {
    }
}
