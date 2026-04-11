package com.supervisesuite.backend.storage;

import com.supervisesuite.backend.common.error.ServiceUnavailableException;
import java.time.Duration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class S3StorageService implements StorageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3StorageService(S3Presigner s3Presigner, S3Client s3Client, S3Properties s3Properties) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public String getUploadUrl(String s3Key, String contentType) {
        String bucket = requiredBucket();
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(60, s3Properties.getUploadUrlTtlSeconds())))
                .putObjectRequest(putObjectRequest)
                .build();

            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("Unable to create upload URL.", exception);
        }
    }

    @Override
    public String getDownloadUrl(String s3Key) {
        String bucket = requiredBucket();
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(Math.max(60, s3Properties.getDownloadUrlTtlSeconds())))
                .getObjectRequest(getObjectRequest)
                .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("Unable to create download URL.", exception);
        }
    }

    @Override
    public void delete(String s3Key) {
        String bucket = requiredBucket();
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(s3Key).build());
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("Unable to delete file from storage.", exception);
        }
    }

    private String requiredBucket() {
        String bucket = s3Properties.getBucketName();
        if (bucket == null || bucket.isBlank()) {
            throw new ServiceUnavailableException("Storage bucket is not configured.");
        }
        return bucket.trim();
    }
}
