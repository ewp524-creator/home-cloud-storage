package com.filehome.upload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.filehome.file.FileView;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class MultipartUploadDtos {

    private MultipartUploadDtos() {
    }

    public record InitiateUploadRequest(
            UUID parentId,
            @NotBlank @Size(max = 255) String fileName,
            @Positive long sizeBytes,
            @Size(max = 255) String contentType,
            @NotBlank
            @Pattern(regexp = "^[a-fA-F0-9]{64}$")
            String clientFingerprint) {
    }

    public record UploadPartView(
            int partNumber,
            long sizeBytes,
            String sha256) {
    }

    public record UploadSessionView(
            UUID id,
            UUID targetEntryId,
            UUID parentId,
            String fileName,
            String contentType,
            String clientFingerprint,
            long sizeBytes,
            long partSizeBytes,
            int totalParts,
            MultipartUploadStatus status,
            Instant expiresAt,
            List<UploadPartView> uploadedParts) {
    }

    public record UploadSessionList(List<UploadSessionView> items) {
    }

    public record UploadPartResult(
            int partNumber,
            long sizeBytes,
            String sha256) {
    }

    public record CompleteUploadResponse(FileView file) {
    }
}
