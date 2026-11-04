package com.filehome.auth.session;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionView(
        UUID id,
        String userAgent,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current) {
}
