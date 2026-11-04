package com.filehome.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FileView(
        UUID id,
        String name,
        String kind,
        UUID parentId,
        long size,
        String owner,
        boolean starred,
        List<String> tags,
        boolean shared,
        long itemCount,
        Instant modifiedAt,
        Instant deletedAt,
        String mimeType,
        String extension,
        String preview) {

    public static FileView from(
            FileEntry entry,
            String ownerName,
            long itemCount,
            boolean shared) {
        String extension = FilePreviewType.extensionOf(entry.name());
        return new FileView(
                entry.id(),
                entry.name(),
                entry.isFolder() ? "folder" : "file",
                entry.parentId(),
                entry.size(),
                ownerName,
                entry.starred(),
                entry.tags(),
                shared,
                itemCount,
                entry.updatedAt(),
                entry.deletedAt(),
                entry.mimeType(),
                extension,
                FilePreviewType.previewKind(entry.name(), entry.mimeType()));
    }
}
