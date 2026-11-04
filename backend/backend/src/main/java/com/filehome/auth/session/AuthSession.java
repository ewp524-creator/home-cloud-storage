package com.filehome.auth.session;

import java.time.Instant;
import java.util.UUID;

public record AuthSession(
        UUID id,
        UUID userId,
        byte[] tokenHash,
        String userAgent,
        byte[] createdIpHash,
        Instant createdAt,
        Instant expiresAt) {
}
