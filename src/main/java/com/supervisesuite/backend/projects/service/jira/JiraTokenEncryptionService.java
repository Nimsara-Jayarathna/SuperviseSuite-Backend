package com.supervisesuite.backend.projects.service.jira;

import com.supervisesuite.backend.common.error.ValidationException;
import com.supervisesuite.backend.config.JiraProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JiraTokenEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String VERSION_PREFIX = "v1";

    private final JiraProperties jiraProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public JiraTokenEncryptionService(JiraProperties jiraProperties) {
        this.jiraProperties = jiraProperties;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new ValidationException("jiraOAuth", "Jira access token was empty.");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return VERSION_PREFIX + ":" + Base64.getEncoder().encodeToString(iv) + ":"
                + Base64.getEncoder().encodeToString(encrypted);
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ValidationException("jiraOAuth", "Failed to encrypt Jira token.");
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new ValidationException("jiraOAuth", "Encrypted Jira token was empty.");
        }
        try {
            String[] parts = encryptedValue.split(":", 3);
            if (parts.length != 3 || !VERSION_PREFIX.equals(parts[0])) {
                throw new ValidationException("jiraOAuth", "Unsupported Jira token encryption format.");
            }

            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ValidationException("jiraOAuth", "Failed to decrypt Jira token.");
        }
    }

    private SecretKeySpec resolveKey() throws Exception {
        String secret = jiraProperties.getTokenEncryptionSecret();
        if (secret == null || secret.isBlank()) {
            throw new ValidationException("jiraConfig", "Jira token encryption secret is not configured.");
        }
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }
}
