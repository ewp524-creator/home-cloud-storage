package com.filehome.activity;

import java.util.UUID;

public record ActivityEvent(
        UUID eventId,
        UUID ownerId,
        String action,
        UUID entryId,
        long occurredAtEpochMillis) {
}
