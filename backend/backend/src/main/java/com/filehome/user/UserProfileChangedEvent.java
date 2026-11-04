package com.filehome.user;

import java.util.UUID;

public record UserProfileChangedEvent(UUID userId) {
}
