package com.supervisesuite.backend.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Small cryptographic helpers that should be injectable and mockable.
 */
@Component
public class CryptoUtils {

    private final SecureRandom secureRandom;

    public CryptoUtils() {
        this(new SecureRandom());
    }

    CryptoUtils(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    /**
     * Generates a random opaque token: 32 random bytes encoded using URL-safe Base64 without padding.
     */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes SHA-256 of {@code raw} (UTF-8) and returns the digest encoded in standard Base64.
     */
    public String sha256Base64(String raw) {
        byte[] bytes = raw == null ? new byte[0] : raw.getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(sha256(bytes));
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}

