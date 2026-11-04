package com.filehome.auth.session;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.filehome.config.AppProperties;

@Service
public class SessionTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] CSRF_CONTEXT =
            "filehome-csrf-v1".getBytes(StandardCharsets.UTF_8);

    private final byte[] csrfKey;

    @Autowired
    public SessionTokenService(AppProperties properties) {
        this(properties.jwt().secret());
    }

    SessionTokenService(String jwtSecret) {
        if (jwtSecret == null
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "FILEHOME_JWT_SECRET must contain at least 32 bytes");
        }
        this.csrfKey = hmac(
                jwtSecret.getBytes(StandardCharsets.UTF_8),
                CSRF_CONTEXT);
    }

    public String newToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String hashHex(String token) {
        return HexFormat.of().formatHex(hash(token));
    }

    public String csrfFor(String sessionToken) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(hmac(
                        csrfKey,
                        sessionToken.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean csrfMatches(String sessionToken, String candidate) {
        if (sessionToken == null
                || sessionToken.isBlank()
                || candidate == null
                || candidate.isBlank()) {
            return false;
        }
        byte[] expected = csrfFor(sessionToken).getBytes(StandardCharsets.UTF_8);
        byte[] actual = candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    HMAC_ALGORITHM + " is unavailable",
                    exception);
        }
    }
}
