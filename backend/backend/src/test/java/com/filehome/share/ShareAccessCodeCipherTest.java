package com.filehome.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.filehome.config.AppProperties;

class ShareAccessCodeCipherTest {

    private final ShareAccessCodeCipher cipher = new ShareAccessCodeCipher(
            new AppProperties(
                    new AppProperties.Jwt("jwt-secret", 120),
                    new AppProperties.Auth(12, false, 60),
                    new AppProperties.Upload(
                            16 * 1024 * 1024,
                            1024L * 1024 * 1024,
                            48,
                            4),
                    new AppProperties.Storage(
                            "http://localhost:9000",
                            "http://localhost:9001",
                            "access",
                            "secret",
                            "file-home",
                            "/tmp/filehome"),
                    new AppProperties.Share(
                            "http://localhost",
                            "share-secret-with-enough-entropy"),
                    new AppProperties.Cors(List.of("http://localhost")),
                    new AppProperties.Cache(30, 60, 30)));

    @Test
    void roundTripsAccessCodeWithoutStoringPlaintext() {
        String encrypted = cipher.encrypt("ABC123");

        assertEquals("ABC123", cipher.decrypt(encrypted));
        assertFalse(encrypted.contains("ABC123"));
    }

    @Test
    void returnsNullForEmptyOrInvalidCiphertext() {
        assertNull(cipher.encrypt(""));
        assertNull(cipher.decrypt(""));
        assertNull(cipher.decrypt("not-base64"));
    }
}
