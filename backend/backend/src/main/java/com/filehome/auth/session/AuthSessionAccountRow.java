package com.filehome.auth.session;

import java.time.Instant;
import java.util.UUID;

import com.filehome.user.UserAccount;

public record AuthSessionAccountRow(
        UUID sessionId,
        Instant sessionExpiresAt,
        UUID userId,
        String username,
        String email,
        String passwordHash,
        String displayName,
        String avatarUrl,
        long storageQuotaBytes,
        boolean enabled,
        Instant userCreatedAt) {

    public AuthSessionPrincipal toPrincipal() {
        return new AuthSessionPrincipal(
                sessionId,
                sessionExpiresAt,
                new UserAccount(
                        userId,
                        username,
                        email,
                        passwordHash,
                        displayName,
                        avatarUrl,
                        storageQuotaBytes,
                        enabled,
                        userCreatedAt));
    }
}
