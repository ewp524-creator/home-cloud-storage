package com.filehome.upload;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.filehome.common.ApiException;
import com.filehome.config.AppProperties;
import com.filehome.file.FileEntry;
import com.filehome.file.FilePreviewType;
import com.filehome.file.FileRepository;
import com.filehome.file.FileService;
import com.filehome.file.FileView;
import com.filehome.security.RequestRateLimiter;
import com.filehome.storage.MultipartUploadStoragePort;
import com.filehome.storage.MultipartUploadStoragePort.StoredPart;
import com.filehome.storage.ObjectStoragePort;
import com.filehome.upload.MultipartUploadDtos.CompleteUploadResponse;
import com.filehome.upload.MultipartUploadDtos.InitiateUploadRequest;
import com.filehome.upload.MultipartUploadDtos.UploadPartResult;
import com.filehome.upload.MultipartUploadDtos.UploadPartView;
import com.filehome.upload.MultipartUploadDtos.UploadSessionList;
import com.filehome.upload.MultipartUploadDtos.UploadSessionView;
import com.filehome.user.UserAccount;
import com.filehome.user.UserService;

@Service
public class MultipartUploadService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MultipartUploadService.class);
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;

    private final MultipartUploadRepository uploadRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;
    private final UserService userService;
    private final ObjectStoragePort objectStorage;
    private final MultipartUploadStoragePort multipartStorage;
    private final UploadPartLock partLock;
    private final RequestRateLimiter rateLimiter;
    private final TransactionTemplate transactions;
    private final long partSizeBytes;
    private final long maxFileSizeBytes;
    private final long sessionHours;
    private final int maxActiveSessions;

    public MultipartUploadService(
            MultipartUploadRepository uploadRepository,
            FileRepository fileRepository,
            FileService fileService,
            UserService userService,
            ObjectStoragePort objectStorage,
            MultipartUploadStoragePort multipartStorage,
            UploadPartLock partLock,
            RequestRateLimiter rateLimiter,
            PlatformTransactionManager transactionManager,
            AppProperties properties) {
        this.uploadRepository = uploadRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.userService = userService;
        this.objectStorage = objectStorage;
        this.multipartStorage = multipartStorage;
        this.partLock = partLock;
        this.rateLimiter = rateLimiter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.partSizeBytes = properties.upload().partSizeBytes();
        this.maxFileSizeBytes = properties.upload().maxFileSizeBytes();
        this.sessionHours = properties.upload().sessionHours();
        this.maxActiveSessions = properties.upload().maxActiveSessions();
        validateConfiguration();
    }

    public UploadSessionView initiate(
            UUID userId,
            InitiateUploadRequest request) {
        if (request.sizeBytes() > maxFileSizeBytes) {
            throw ApiException.badRequest(
                    "FILE_TOO_LARGE",
                    "\u6587\u4ef6\u8d85\u8fc7\u670d\u52a1\u5668\u5141\u8bb8\u7684\u6700\u5927\u4e0a\u4f20\u5927\u5c0f");
        }
        String fileName = fileService.safeFileName(request.fileName());
        String contentType = FilePreviewType.contentType(
                fileName,
                fileService.safeMimeType(request.contentType()));
        int totalParts = totalParts(request.sizeBytes());
        InitiateState state = new InitiateState();
        try {
            MultipartUploadSession session = transactions.execute(status -> {
                UserAccount user = userService.requireForUpdate(userId);
                FileEntry target = fileService.resolveTargetFolder(
                        userId,
                        request.parentId());
                if (fileRepository.countActiveChildByName(
                        userId,
                        target.id(),
                        fileName) > 0) {
                    throw ApiException.conflict(
                            "NAME_CONFLICT",
                            "\u540c\u540d\u6587\u4ef6\u5df2\u5b58\u5728");
                }
                if (uploadRepository.countActiveByOwner(userId)
                        >= maxActiveSessions) {
                    throw ApiException.conflict(
                            "TOO_MANY_UPLOADS",
                            "\u5e76\u53d1\u4e0a\u4f20\u4efb\u52a1\u8fc7\u591a\uff0c\u8bf7\u5148\u5b8c\u6210\u6216\u53d6\u6d88\u5176\u4ed6\u4e0a\u4f20");
                }
                long used = fileRepository.storageStats(userId).usedBytes();
                long reserved = uploadRepository.sumReservedBytes(userId);
                ensureQuota(user, used, reserved, request.sizeBytes());

                UUID sessionId = UUID.randomUUID();
                UUID fileId = UUID.randomUUID();
                String bucket = objectStorage.bucketName();
                String objectKey = user.username() + "/" + fileId;
                state.uploadId = multipartStorage.create(
                        bucket,
                        objectKey,
                        contentType);
                state.bucket = bucket;
                state.objectKey = objectKey;
                Instant now = Instant.now();
                MultipartUploadSession created = new MultipartUploadSession(
                        sessionId,
                        userId,
                        fileId,
                        target.id(),
                        fileName,
                        contentType,
                        request.clientFingerprint().toLowerCase(),
                        request.sizeBytes(),
                        partSizeBytes,
                        totalParts,
                        bucket,
                        objectKey,
                        state.uploadId,
                        MultipartUploadStatus.INITIATED,
                        now,
                        now,
                        now.plusSeconds(sessionHours * 3600));
                uploadRepository.insert(created);
                return created;
            });
            return toView(session, List.of());
        } catch (RuntimeException exception) {
            abortCreatedStorage(state);
            throw exception;
        }
    }

    public UploadSessionView get(UUID userId, UUID uploadId) {
        MultipartUploadSession session = requireSession(userId, uploadId);
        return toView(session, uploadRepository.findParts(uploadId));
    }

    public UploadSessionList listActive(UUID userId) {
        List<UploadSessionView> items = uploadRepository
                .findActiveByOwner(userId)
                .stream()
                .map(session -> toView(
                        session,
                        uploadRepository.findParts(session.id())))
                .toList();
        return new UploadSessionList(items);
    }

    public UploadPartResult uploadPart(
            UUID userId,
            UUID uploadId,
            int partNumber,
            long contentLength,
            String expectedSha256,
            InputStream inputStream) {
        rateLimiter.check(
                "multipart-part:" + userId,
                1200,
                Duration.ofMinutes(1));
        MultipartUploadSession session = requireActiveSession(userId, uploadId);
        validatePartNumber(session, partNumber);
        long expectedSize = expectedPartSize(session, partNumber);
        if (contentLength != expectedSize) {
            throw ApiException.badRequest(
                    "INVALID_PART_SIZE",
                    "\u5206\u7247\u5927\u5c0f\u4e0e\u4e0a\u4f20\u4f1a\u8bdd\u4e0d\u4e00\u81f4");
        }
        String expectedHash = UploadChunkMath.normalizeSha256(expectedSha256);
        String lockToken = partLock.acquire(uploadId, partNumber);
        try {
            MessageDigest digest = sha256Digest();
            try (DigestInputStream digestInput =
                    new DigestInputStream(inputStream, digest)) {
                StoredPart stored = multipartStorage.uploadPart(
                        session.storageBucket(),
                        session.storageObjectKey(),
                        session.minioUploadId(),
                        partNumber,
                        expectedSize,
                        digestInput);
                String actualHash = HexFormat.of().formatHex(digest.digest());
                if (!actualHash.equals(expectedHash)) {
                    throw ApiException.badRequest(
                            "CHUNK_CHECKSUM_MISMATCH",
                            "\u5206\u7247\u5b8c\u6574\u6027\u6821\u9a8c\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5");
                }
                MultipartUploadPart part = new MultipartUploadPart(
                        uploadId,
                        partNumber,
                        stored.etag(),
                        HexFormat.of().parseHex(actualHash),
                        expectedSize,
                        Instant.now());
                transactions.executeWithoutResult(status -> {
                    uploadRepository.upsertPart(part);
                    uploadRepository.markUploading(uploadId);
                });
                return new UploadPartResult(
                        partNumber,
                        expectedSize,
                        actualHash);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Cannot read multipart request body",
                        exception);
            }
        } finally {
            partLock.release(uploadId, partNumber, lockToken);
        }
    }

    public CompleteUploadResponse complete(UUID userId, UUID uploadId) {
        MultipartUploadSession session = requireSession(userId, uploadId);
        FileView completed = completedFileIfAvailable(userId, session);
        if (completed != null) {
            return new CompleteUploadResponse(completed);
        }
        if (session.status() == MultipartUploadStatus.COMPLETING) {
            OptionalLong finalSize = multipartStorage.objectSize(
                    session.storageBucket(),
                    session.storageObjectKey());
            if (finalSize.isPresent()) {
                validateFinalObject(session, finalSize.getAsLong());
                return new CompleteUploadResponse(
                        finalizeCompleted(session));
            }
        }
        ensureNotExpired(session);
        if (session.status() == MultipartUploadStatus.ABORTED) {
            throw ApiException.conflict(
                    "UPLOAD_ABORTED",
                    "\u4e0a\u4f20\u4efb\u52a1\u5df2\u53d6\u6d88");
        }

        List<MultipartUploadPart> parts = uploadRepository.findParts(uploadId);
        validateParts(session, parts);
        transactions.executeWithoutResult(status -> {
            MultipartUploadSession locked = requireLockedSession(userId, uploadId);
            ensureNotExpired(locked);
            uploadRepository.markCompleting(uploadId);
        });
        try {
            multipartStorage.complete(
                    session.storageBucket(),
                    session.storageObjectKey(),
                    session.minioUploadId(),
                    parts.stream()
                            .map(part -> new StoredPart(
                                    part.partNumber(),
                                    part.etag(),
                                    part.sizeBytes()))
                            .toList());
            long finalSize = multipartStorage.objectSize(
                            session.storageBucket(),
                            session.storageObjectKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Completed multipart object is missing"));
            validateFinalObject(session, finalSize);
            return new CompleteUploadResponse(finalizeCompleted(session));
        } catch (DuplicateKeyException exception) {
            abortAfterConflict(session);
            throw ApiException.conflict(
                    "NAME_CONFLICT",
                    "\u540c\u540d\u6587\u4ef6\u5df2\u5b58\u5728\uff0c\u4e0a\u4f20\u5df2\u53d6\u6d88");
        }
    }

    public void abort(UUID userId, UUID uploadId) {
        MultipartUploadSession session = requireSession(userId, uploadId);
        if (session.status() == MultipartUploadStatus.COMPLETED) {
            throw ApiException.conflict(
                    "UPLOAD_COMPLETED",
                    "\u5df2\u5b8c\u6210\u7684\u4e0a\u4f20\u4e0d\u80fd\u53d6\u6d88");
        }
        transactions.executeWithoutResult(status ->
                uploadRepository.markAborted(uploadId));
        abortStorage(session);
    }

    public int cleanupExpired() {
        List<MultipartUploadSession> expired =
                uploadRepository.findExpired(100);
        int removed = 0;
        for (MultipartUploadSession session : expired) {
            try {
                transactions.executeWithoutResult(status ->
                        uploadRepository.markAborted(session.id()));
                abortStorage(session);
                removed += 1;
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Could not clean expired upload {}",
                        session.id(),
                        exception);
            }
        }
        uploadRepository.deleteTerminalBefore();
        return removed;
    }

    private FileView finalizeCompleted(MultipartUploadSession session) {
        return transactions.execute(status -> {
            MultipartUploadSession locked = requireLockedSession(
                    session.ownerId(),
                    session.id());
            if (locked.status() == MultipartUploadStatus.COMPLETED) {
                return fileService.details(
                        locked.ownerId(),
                        locked.targetEntryId());
            }
            if (locked.status() != MultipartUploadStatus.COMPLETING) {
                throw ApiException.conflict(
                        "UPLOAD_NOT_COMPLETING",
                        "\u4e0a\u4f20\u4f1a\u8bdd\u72b6\u6001\u4e0d\u5141\u8bb8\u5b8c\u6210");
            }
            FileView file = fileService.registerCompletedUpload(
                    locked.ownerId(),
                    locked.parentId(),
                    locked.targetEntryId(),
                    locked.fileName(),
                    locked.storageBucket(),
                    locked.storageObjectKey(),
                    locked.sizeBytes(),
                    locked.mimeType());
            uploadRepository.markCompleted(locked.id());
            return file;
        });
    }

    private FileView completedFileIfAvailable(
            UUID userId,
            MultipartUploadSession session) {
        if (session.status() != MultipartUploadStatus.COMPLETED) {
            return null;
        }
        return fileService.details(userId, session.targetEntryId());
    }

    private void abortAfterConflict(MultipartUploadSession session) {
        try {
            transactions.executeWithoutResult(status ->
                    uploadRepository.markAborted(session.id()));
            objectStorage.delete(
                    session.storageBucket(),
                    session.storageObjectKey());
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn(
                    "Could not remove conflicting upload {}",
                    session.id(),
                    cleanupFailure);
        }
    }

    private void abortStorage(MultipartUploadSession session) {
        try {
            multipartStorage.abort(
                    session.storageBucket(),
                    session.storageObjectKey(),
                    session.minioUploadId());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not abort MinIO multipart upload {}",
                    session.id(),
                    exception);
        }
        try {
            objectStorage.delete(
                    session.storageBucket(),
                    session.storageObjectKey());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not remove completed object for upload {}",
                    session.id(),
                    exception);
        }
    }

    private void abortCreatedStorage(InitiateState state) {
        if (state.uploadId == null) {
            return;
        }
        try {
            multipartStorage.abort(
                    state.bucket,
                    state.objectKey,
                    state.uploadId);
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn(
                    "Could not abort upload after initiation failure",
                    cleanupFailure);
        }
    }

    private MultipartUploadSession requireSession(UUID userId, UUID uploadId) {
        return uploadRepository.findByIdAndOwner(uploadId, userId)
                .orElseThrow(() -> ApiException.notFound(
                        "\u4e0a\u4f20\u4efb\u52a1\u4e0d\u5b58\u5728"));
    }

    private MultipartUploadSession requireLockedSession(
            UUID userId,
            UUID uploadId) {
        return uploadRepository.findByIdAndOwnerForUpdate(uploadId, userId)
                .orElseThrow(() -> ApiException.notFound(
                        "\u4e0a\u4f20\u4efb\u52a1\u4e0d\u5b58\u5728"));
    }

    private MultipartUploadSession requireActiveSession(
            UUID userId,
            UUID uploadId) {
        MultipartUploadSession session = requireSession(userId, uploadId);
        ensureNotExpired(session);
        if (session.status() != MultipartUploadStatus.INITIATED
                && session.status() != MultipartUploadStatus.UPLOADING) {
            throw ApiException.conflict(
                    "UPLOAD_NOT_ACTIVE",
                    "\u4e0a\u4f20\u4f1a\u8bdd\u5df2\u4e0d\u5728\u4e0a\u4f20\u72b6\u6001");
        }
        return session;
    }

    private void ensureNotExpired(MultipartUploadSession session) {
        if (!session.expiresAt().isAfter(Instant.now())) {
            throw ApiException.conflict(
                    "UPLOAD_EXPIRED",
                    "\u4e0a\u4f20\u4f1a\u8bdd\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u5f00\u59cb");
        }
    }

    private void validatePartNumber(
            MultipartUploadSession session,
            int partNumber) {
        UploadChunkMath.partSize(
                session.sizeBytes(),
                session.partSizeBytes(),
                session.totalParts(),
                partNumber);
    }

    private long expectedPartSize(
            MultipartUploadSession session,
            int partNumber) {
        return UploadChunkMath.partSize(
                session.sizeBytes(),
                session.partSizeBytes(),
                session.totalParts(),
                partNumber);
    }

    private void validateParts(
            MultipartUploadSession session,
            List<MultipartUploadPart> parts) {
        if (parts.size() != session.totalParts()) {
            throw ApiException.conflict(
                    "UPLOAD_INCOMPLETE",
                    "\u4ecd\u6709\u5206\u7247\u672a\u4e0a\u4f20");
        }
        for (int index = 0; index < parts.size(); index++) {
            MultipartUploadPart part = parts.get(index);
            int expectedNumber = index + 1;
            if (part.partNumber() != expectedNumber
                    || part.sizeBytes()
                    != expectedPartSize(session, expectedNumber)) {
                throw ApiException.conflict(
                        "UPLOAD_PARTS_INVALID",
                        "\u5206\u7247\u5e8f\u5217\u4e0d\u5b8c\u6574\u6216\u5927\u5c0f\u4e0d\u4e00\u81f4");
            }
        }
    }

    private void validateFinalObject(
            MultipartUploadSession session,
            long finalSize) {
        if (finalSize != session.sizeBytes()) {
            throw ApiException.conflict(
                    "FINAL_SIZE_MISMATCH",
                    "\u5408\u5e76\u540e\u7684\u6587\u4ef6\u5927\u5c0f\u4e0d\u6b63\u786e");
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int totalParts(long sizeBytes) {
        return UploadChunkMath.totalParts(sizeBytes, partSizeBytes);
    }

    private void ensureQuota(
            UserAccount user,
            long used,
            long reserved,
            long additional) {
        long committed;
        try {
            committed = Math.addExact(used, reserved);
        } catch (ArithmeticException exception) {
            throw ApiException.quotaExceeded(
                    "\u5b58\u50a8\u7a7a\u95f4\u4e0d\u8db3");
        }
        long available = Math.max(0, user.storageQuotaBytes() - committed);
        if (additional > available) {
            throw ApiException.quotaExceeded(
                    "\u5b58\u50a8\u7a7a\u95f4\u4e0d\u8db3\uff0c\u4e0a\u4f20\u4efb\u52a1\u65e0\u6cd5\u521b\u5efa");
        }
    }

    private UploadSessionView toView(
            MultipartUploadSession session,
            List<MultipartUploadPart> parts) {
        return new UploadSessionView(
                session.id(),
                session.targetEntryId(),
                session.parentId(),
                session.fileName(),
                session.mimeType(),
                session.clientFingerprint(),
                session.sizeBytes(),
                session.partSizeBytes(),
                session.totalParts(),
                session.status(),
                session.expiresAt(),
                parts.stream()
                        .map(part -> new UploadPartView(
                                part.partNumber(),
                                part.sizeBytes(),
                                HexFormat.of().formatHex(part.sha256())))
                        .toList());
    }

    private void validateConfiguration() {
        if (partSizeBytes < MIN_PART_SIZE
                || partSizeBytes > 5L * 1024 * 1024 * 1024) {
            throw new IllegalStateException(
                    "FILEHOME_UPLOAD_PART_SIZE_BYTES must be between 5 MiB and 5 GiB");
        }
        if (maxFileSizeBytes <= 0
                || sessionHours <= 0
                || maxActiveSessions <= 0) {
            throw new IllegalStateException(
                    "Upload limits must be positive");
        }
    }

    private static final class InitiateState {
        private String uploadId;
        private String bucket;
        private String objectKey;
    }
}
