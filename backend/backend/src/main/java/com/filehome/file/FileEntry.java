package com.filehome.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FileEntry(
        UUID id,
        UUID ownerId,
        UUID parentId,
        EntryKind kind,
        String name,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        String bucket,
        String objectKey,
        long size,
        String mimeType,
        boolean starred,
        List<String> tags,
        boolean shared) {

    public enum EntryKind {
        FILE,
        FOLDER
    }

    public boolean isFolder() {
        return kind == EntryKind.FOLDER;
    }
}
