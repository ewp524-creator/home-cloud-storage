package com.filehome.user;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String username,
        String email,
        String passwordHash,
        String displayName,
        String avatarUrl,
        long storageQuotaBytes,
        boolean enabled,
        Instant createdAt) {
}
