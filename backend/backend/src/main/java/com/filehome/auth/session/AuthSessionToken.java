package com.filehome.auth.session;

import java.time.Instant;
import java.util.UUID;

public record AuthSessionToken(
        UUID id,
        UUID userId,
        byte[] tokenHash,
        Instant expiresAt) {
}
