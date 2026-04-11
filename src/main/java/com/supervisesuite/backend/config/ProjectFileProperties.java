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
@ConfigurationProperties(prefix = "project-files")
public class ProjectFileProperties {
    private long maxFileSizeBytes = 10L * 1024L * 1024L;
    private List<String> allowedTypes = new ArrayList<>(List.of("pdf", "docx", "pptx", "zip"));
}
