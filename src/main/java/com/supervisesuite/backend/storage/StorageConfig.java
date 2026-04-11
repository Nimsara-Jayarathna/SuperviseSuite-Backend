package com.supervisesuite.backend.storage;

import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.supervisesuite.backend.common.error.ServiceUnavailableException;

@Configuration
public class StorageConfig {

    @Bean
    public StorageService storageService(S3StorageService s3StorageService) {
        return s3StorageService;
    }

    @Bean
    public S3Client s3Client(S3Properties s3Properties) {
        S3ClientBuilder builder = S3Client.builder()
            .region(resolveRegion(s3Properties))
            .credentialsProvider(resolveCredentialsProvider(s3Properties));

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties s3Properties) {
        S3Presigner.Builder builder = S3Presigner.builder()
            .region(resolveRegion(s3Properties))
            .credentialsProvider(resolveCredentialsProvider(s3Properties));

        return builder.build();
    }

    private Region resolveRegion(S3Properties s3Properties) {
        String configured = trimToNull(s3Properties.getRegion());
        if (!StringUtils.hasText(configured)) {
            throw new ServiceUnavailableException("AWS region is not configured.");
        }
        return Region.of(configured);
    }

    private StaticCredentialsProvider resolveCredentialsProvider(S3Properties s3Properties) {
        String accessKeyId = trimToNull(s3Properties.getAccessKeyId());
        String secretAccessKey = trimToNull(s3Properties.getSecretAccessKey());
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
