package com.filehome.file;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.filehome.common.ApiException;
import com.filehome.storage.ObjectStoragePort;

@Service
public class FilePreviewService {

    public static final String SCOPE_PERSONAL = "personal";
    public static final String SCOPE_SHARE = "share";

    private final FileService fileService;
    private final ObjectStoragePort objectStorage;
    private final PreviewTicketService ticketService;
    private final SharedPreviewAuthorizer sharedPreviewAuthorizer;

    public FilePreviewService(
            FileService fileService,
            ObjectStoragePort objectStorage,
            PreviewTicketService ticketService,
            SharedPreviewAuthorizer sharedPreviewAuthorizer) {
        this.fileService = fileService;
        this.objectStorage = objectStorage;
        this.ticketService = ticketService;
        this.sharedPreviewAuthorizer = sharedPreviewAuthorizer;
    }

    public PreviewAccess personalAccess(UUID userId, UUID fileId) {
        FileEntry entry = requirePreviewable(userId, fileId);
        return access(
                SCOPE_PERSONAL,
                userId,
                entry);
    }

    public PreviewAccess sharedAccess(UUID shareId, FileEntry root) {
        if (root.isFolder()) {
            throw ApiException.badRequest(
                    "FOLDER_PREVIEW_UNSUPPORTED",
                    "文件夹请先打开其中的文件再预览");
        }
        return access(SCOPE_SHARE, shareId, root);
    }

    public FileEntry requireTicket(String ticket, UUID requestedFileId) {
        PreviewTicketService.Claims claims = ticketService.verify(ticket);
        if (!requestedFileId.equals(claims.fileId())) {
            throw ApiException.forbidden("预览链接与文件不匹配");
        }
        if (SCOPE_PERSONAL.equals(claims.scope())) {
            if (!claims.actorId().equals(claims.ownerId())) {
                throw ApiException.forbidden("预览链接无效");
            }
        } else if (SCOPE_SHARE.equals(claims.scope())) {
            sharedPreviewAuthorizer.requireActive(claims.ownerId(), claims.actorId());
        } else {
            throw ApiException.forbidden("预览链接无效");
        }
        return requirePreviewable(claims.ownerId(), claims.fileId());
    }

    public String contentType(FileEntry entry) {
        return FilePreviewType.contentType(entry.name(), entry.mimeType());
    }

    public String previewKind(FileEntry entry) {
        return FilePreviewType.previewKind(entry.name(), contentType(entry));
    }

    public InputStream openRange(FileEntry entry, long offset, long length) {
        if (entry.isFolder() || entry.bucket() == null || entry.objectKey() == null) {
            throw ApiException.badRequest("NOT_A_FILE", "该条目不是文件");
        }
        return objectStorage.get(
                entry.bucket(),
                entry.objectKey(),
                offset,
                length);
    }

    private PreviewAccess access(
            String scope,
            UUID actorId,
            FileEntry entry) {
        PreviewTicketService.Ticket ticket = ticketService.issue(
                scope,
                actorId,
                entry.ownerId(),
                entry.id());
        String kind = previewKind(entry);
        return new PreviewAccess(
                "/api/v1/media/" + entry.id() + "?ticket=" + ticket.value(),
                ticket.expiresAt(),
                kind,
                contentType(entry),
                entry.size(),
                true,
                FilePreviewType.isTextPreview(kind));
    }

    private FileEntry requirePreviewable(UUID userId, UUID fileId) {
        FileEntry entry = fileService.requireActive(userId, fileId);
        if (entry.isFolder()) {
            throw ApiException.badRequest("NOT_A_FILE", "文件夹不能直接预览");
        }
        return entry;
    }

    public record PreviewAccess(
            String url,
            Instant expiresAt,
            String previewKind,
            String contentType,
            long size,
            boolean rangeSupported,
            boolean textPreview) {
    }

    public record ByteRange(long start, long end) {

        public long length() {
            return end - start + 1;
        }
    }
}
