package com.filehome.share;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.filehome.config.AppProperties;

@Component
public class ShareAccessCodeCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public ShareAccessCodeCipher(AppProperties properties) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.share().tokenSecret()
                            .getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(
                    Arrays.copyOf(digest, 32),
                    "AES");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot initialize share access-code encryption",
                    exception);
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(
                    value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot encrypt share access code",
                    exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= IV_BYTES) {
                return null;
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(
                    payload,
                    IV_BYTES,
                    payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(
                    cipher.doFinal(encrypted),
                    StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }
}
