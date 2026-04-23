package com.supervisesuite.backend.storage;

import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import com.supervisesuite.backend.config.ProjectFileProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class StorageConfig {

    @Bean
    public StorageService storageService(S3StorageService s3StorageService) {
        return s3StorageService;
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(ProjectFileProperties projectFileProperties) {
        S3ClientBuilder builder = S3Client.builder()
            .region(resolveRegion(projectFileProperties))
            .credentialsProvider(resolveCredentialsProvider(projectFileProperties));

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(ProjectFileProperties projectFileProperties) {
        S3Presigner.Builder builder = S3Presigner.builder()
            .region(resolveRegion(projectFileProperties))
            .credentialsProvider(resolveCredentialsProvider(projectFileProperties));

        return builder.build();
    }

    private Region resolveRegion(ProjectFileProperties projectFileProperties) {
        String configured = trimToNull(projectFileProperties.getAwsRegion());
        if (!StringUtils.hasText(configured)) {
            throw new ServiceUnavailableException("AWS region is not configured.");
        }
        return Region.of(configured);
    }

    private StaticCredentialsProvider resolveCredentialsProvider(ProjectFileProperties projectFileProperties) {
        String accessKeyId = trimToNull(projectFileProperties.getAwsAccessKeyId());
        String secretAccessKey = trimToNull(projectFileProperties.getAwsSecretAccessKey());
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(secretAccessKey)) {
            throw new ServiceUnavailableException("AWS credentials are not configured.");
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        return StaticCredentialsProvider.create(credentials);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
