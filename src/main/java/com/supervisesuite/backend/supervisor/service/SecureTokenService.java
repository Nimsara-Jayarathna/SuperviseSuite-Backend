package com.supervisesuite.backend.supervisor.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
class SecureTokenService {

    private final SecureRandom secureRandom = new SecureRandom();

    String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String sha256Base64(String raw) {
        return Base64.getEncoder()
            .encodeToString(sha256Bytes((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] sha256Bytes(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
