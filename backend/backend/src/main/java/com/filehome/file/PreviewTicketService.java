package com.filehome.file;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.filehome.common.ApiException;
import com.filehome.config.AppProperties;

@Component
public class PreviewTicketService {

    private static final String VERSION = "v1";
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private final byte[] secret;

    public PreviewTicketService(AppProperties properties) {
        String tokenSecret = properties.share().tokenSecret();
        if (tokenSecret == null
                || tokenSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "FILEHOME_SHARE_TOKEN_SECRET must contain at least 32 bytes");
        }
        this.secret = tokenSecret.getBytes(StandardCharsets.UTF_8);
    }

    public Ticket issue(
            String scope,
            UUID actorId,
            UUID ownerId,
            UUID fileId) {
        Instant expiresAt = Instant.now().plus(LIFETIME);
        String payload = String.join(
                "|",
                VERSION,
                Long.toString(expiresAt.getEpochSecond()),
                scope,
                actorId.toString(),
                ownerId.toString(),
                fileId.toString());
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sign(encodedPayload));
        return new Ticket(encodedPayload + "." + signature, expiresAt);
    }

    public Claims verify(String ticket) {
        if (ticket == null || ticket.length() > 1024) {
            throw invalidTicket();
        }
        String[] parts = ticket.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw invalidTicket();
        }
        byte[] expected;
        byte[] actual;
        try {
            expected = sign(parts[0]);
            actual = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw invalidTicket();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw invalidTicket();
        }
        String payload;
        try {
            payload = new String(
                    Base64.getUrlDecoder().decode(parts[0]),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidTicket();
        }
        String[] claims = payload.split("\\|", -1);
        if (claims.length != 6 || !VERSION.equals(claims[0])) {
            throw invalidTicket();
        }
        try {
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(claims[1]));
            if (!expiresAt.isAfter(Instant.now())) {
                throw invalidTicket();
            }
            return new Claims(
                    claims[2],
                    UUID.fromString(claims[3]),
                    UUID.fromString(claims[4]),
                    UUID.fromString(claims[5]));
        } catch (IllegalArgumentException exception) {
            throw invalidTicket();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign preview ticket", exception);
        }
    }

    private ApiException invalidTicket() {
        return ApiException.forbidden("预览链接已失效，请重新打开预览");
    }

    public record Ticket(String value, Instant expiresAt) {
    }

    public record Claims(
            String scope,
            UUID actorId,
            UUID ownerId,
            UUID fileId) {
    }
}
