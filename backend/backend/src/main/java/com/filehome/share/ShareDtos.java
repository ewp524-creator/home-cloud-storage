package com.filehome.share;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.filehome.file.FileView;

public final class ShareDtos {

    private ShareDtos() {
    }

    public record CreateShareRequest(
            UUID rootEntryId,
            int days,
            String accessCode,
            String permission,
            boolean recursive) {
    }

    public record ShareView(
            UUID id,
            UUID fileId,
            String fileName,
            String ownerName,
            String ownerInitials,
            String rootType,
            List<UUID> scopeIds,
            long itemCount,
            Instant createdAt,
            Instant expiresAt,
            String accessCode,
            boolean accessCodeConfigured,
            long visits,
            long downloads,
            String permission,
            String status,
            String token,
            String url) {
    }

    public record PublicShareView(
            String token,
            String fileName,
            String ownerName,
            String ownerInitials,
            String rootType,
            String permission,
            String status,
            Instant createdAt,
            Instant expiresAt,
            boolean accessCodeRequired,
            boolean downloadRequiresAuth,
            FileView root,
            List<FileView> folderItems) {
    }

    public record VerifyRequest(
            @jakarta.validation.constraints.Size(max = 16) String accessCode) {
    }

    public record VerifyResponse(boolean valid) {
    }
}
