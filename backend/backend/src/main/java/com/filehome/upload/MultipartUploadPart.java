package com.filehome.upload;

import java.time.Instant;
import java.util.UUID;

public record MultipartUploadPart(
        UUID uploadSessionId,
        int partNumber,
        String etag,
        byte[] sha256,
        long sizeBytes,
        Instant uploadedAt) {
}
