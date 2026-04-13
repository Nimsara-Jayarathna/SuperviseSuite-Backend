package com.supervisesuite.backend.storage;

public interface StorageService {
    String getUploadUrl(String s3Key, String contentType);

    String getDownloadUrl(String s3Key);

    void delete(String s3Key);
}
