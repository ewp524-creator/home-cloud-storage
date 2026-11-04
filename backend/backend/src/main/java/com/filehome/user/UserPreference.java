package com.filehome.user;

import java.time.Instant;
import java.util.UUID;

public record UserPreference(
        UUID userId,
        String defaultView,
        String defaultSort,
        boolean reduceMotion,
        int uploadConcurrency,
        Instant updatedAt) {

    public static UserPreference defaults(UUID userId) {
        return new UserPreference(
                userId,
                "GRID",
                "modified-desc",
                false,
                4,
                Instant.now());
    }
}
