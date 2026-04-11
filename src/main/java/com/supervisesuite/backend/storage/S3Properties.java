package com.supervisesuite.backend.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage.s3")
public class S3Properties {
    private String bucketName;
    private String region;
    private String accessKeyId;
    private String secretAccessKey;
    private long uploadUrlTtlSeconds = 900;
    private long downloadUrlTtlSeconds = 900;
}
