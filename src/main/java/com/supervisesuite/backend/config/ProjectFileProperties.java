package com.supervisesuite.backend.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.project-files")
public class ProjectFileProperties {
    private String awsRegion = "ap-south-1";
    private String bucketName = "supervisesuite-files-local";
    private String awsAccessKeyId;
    private String awsSecretAccessKey;
    private long maxFileSizeBytes = 10L * 1024L * 1024L;
    private int maxFileNameLength = 50;
    private List<String> allowedTypes = new ArrayList<>(List.of("pdf", "docx", "pptx", "zip"));
    private int presignedUrlExpirySeconds = 300;
}
