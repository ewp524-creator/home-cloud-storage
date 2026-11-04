package com.filehome.share;

import java.time.Instant;
import java.util.UUID;

public record ShareLink(
        UUID id,
        UUID ownerId,
        UUID rootEntryId,
        byte[] tokenHash,
        String accessCodeHash,
        String accessCodeEncrypted,
        String permission,
        boolean recursive,
        boolean downloadRequiresAuth,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt) {

    public boolean active() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
