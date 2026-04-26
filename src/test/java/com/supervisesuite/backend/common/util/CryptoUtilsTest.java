package com.supervisesuite.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoUtilsTest {

    @Test
    void generateOpaqueToken_isUrlSafeBase64WithoutPadding_andDecodesTo32Bytes() {
        CryptoUtils cryptoUtils = new CryptoUtils();

        String token = cryptoUtils.generateOpaqueToken();

        assertThat(token).isNotBlank();
        assertThat(token).doesNotContain("=");

        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32);
    }

    @Test
    void sha256Base64_matchesKnownDigest() throws Exception {
        CryptoUtils cryptoUtils = new CryptoUtils();

        String actual = cryptoUtils.sha256Base64("abc");

        byte[] expectedBytes = MessageDigest.getInstance("SHA-256").digest("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String expected = Base64.getEncoder().encodeToString(expectedBytes);

        assertThat(actual).isEqualTo(expected);
    }
}

