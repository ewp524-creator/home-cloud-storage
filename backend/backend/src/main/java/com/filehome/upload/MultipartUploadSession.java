package com.filehome.upload;

import java.time.Instant;
import java.util.UUID;

public record MultipartUploadSession(
        UUID id,
        UUID ownerId,
        UUID targetEntryId,
        UUID parentId,
        String fileName,
        String mimeType,
        String clientFingerprint,
        long sizeBytes,
        long partSizeBytes,
        int totalParts,
        String storageBucket,
        String storageObjectKey,
        String minioUploadId,
        MultipartUploadStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {
}
