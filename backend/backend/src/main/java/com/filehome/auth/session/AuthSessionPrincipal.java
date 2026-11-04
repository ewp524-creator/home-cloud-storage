package com.filehome.auth.session;

import java.time.Instant;
import java.util.UUID;

import com.filehome.user.UserAccount;

public record AuthSessionPrincipal(
        UUID sessionId,
        Instant expiresAt,
        UserAccount user) {
}
