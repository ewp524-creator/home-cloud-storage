package com.filehome.user;

import java.util.UUID;

public record UserView(
        UUID id,
        String username,
        String email,
        String displayName,
        String initials,
        String avatarUrl,
        String plan,
        long storageQuotaBytes) {

    public static UserView from(UserAccount account) {
        String displayName = account.displayName();
        String initials = displayName == null || displayName.isBlank()
                ? "FH"
                : displayName.substring(0, 1).toUpperCase();
        return new UserView(
                account.id(),
                account.username(),
                account.email(),
                displayName,
                initials,
                account.avatarUrl(),
                "\u4e2a\u4eba\u4e91\u7a7a\u95f4",
                account.storageQuotaBytes());
    }
}
