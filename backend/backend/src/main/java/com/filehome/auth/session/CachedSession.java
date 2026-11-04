package com.filehome.auth.session;

import java.time.Instant;
import java.util.UUID;

public record CachedSession(
        UUID sessionId,
        long sessionExpiresAtEpochMillis,
        UUID userId,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        long storageQuotaBytes,
        long userCreatedAtEpochMillis) {

    public static CachedSession from(AuthSessionAccountRow row) {
        return new CachedSession(
                row.sessionId(),
                row.sessionExpiresAt().toEpochMilli(),
                row.userId(),
                row.username(),
                row.email(),
                row.displayName(),
                row.avatarUrl(),
                row.storageQuotaBytes(),
                row.userCreatedAt().toEpochMilli());
    }

    public AuthSessionAccountRow toRow() {
        return new AuthSessionAccountRow(
                sessionId,
                Instant.ofEpochMilli(sessionExpiresAtEpochMillis),
                userId,
                username,
                email,
                "",
                displayName,
                avatarUrl,
                storageQuotaBytes,
                true,
                Instant.ofEpochMilli(userCreatedAtEpochMillis));
    }
}
