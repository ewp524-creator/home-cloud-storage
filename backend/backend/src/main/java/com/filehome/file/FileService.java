package com.filehome.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;

import com.filehome.common.ApiException;
import com.filehome.activity.ActivityEventPublisher;
import com.filehome.file.FileEntry.EntryKind;
import com.filehome.file.FileOperationDtos.CopyRequest;
import com.filehome.file.FileOperationDtos.CreateFolderRequest;
import com.filehome.file.FileOperationDtos.IdsRequest;
import com.filehome.file.FileOperationDtos.MoveRequest;
import com.filehome.file.FileOperationDtos.OperationResult;
import com.filehome.file.FileOperationDtos.RenameRequest;
import com.filehome.file.FileOperationDtos.StarRequest;
import com.filehome.storage.ObjectStoragePort;
import com.filehome.storage.StorageReservationPort;
import com.filehome.storage.StoredObject;
import com.filehome.user.UserAccount;
import com.filehome.user.UserService;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final UserService userService;
    private final ObjectStoragePort objectStorage;
    private final StorageReservationPort storageReservation;
    private final FileMetadataCache metadataCache;
    private final FileShareStatus fileShareStatus;
    private final ActivityEventPublisher activityEventPublisher;

    public FileService(
            FileRepository fileRepository,
            UserService userService,
            ObjectStoragePort objectStorage,
            StorageReservationPort storageReservation,
            FileMetadataCache metadataCache,
            FileShareStatus fileShareStatus,
            ActivityEventPublisher activityEventPublisher) {
        this.fileRepository = fileRepository;
        this.userService = userService;
        this.objectStorage = objectStorage;
        this.storageReservation = storageReservation;
        this.metadataCache = metadataCache;
        this.fileShareStatus = fileShareStatus;
        this.activityEventPublisher = activityEventPublisher;
    }

    public FileListResponse list(
            UUID userId,
            UUID parentId,
            String query,
            String type,
            String tag,
            String sort) {
        return list(userId, parentId, query, type, tag, sort, null, 80);
    }

    public FileListResponse list(
            UUID userId,
            UUID parentId,
            String query,
            String type,
            String tag,
            String sort,
            String cursorToken,
            int limit) {
        String fingerprint = String.join(
                "|",
                parentId == null ? "root" : parentId.toString(),
                normalizeQuery(query) == null ? "" : normalizeQuery(query),
                normalizeType(type),
                normalizeTag(tag) == null ? "" : normalizeTag(tag),
                normalizeSort(sort),
                cursorToken == null ? "" : cursorToken,
                Integer.toString(Math.min(Math.max(limit, 1), 200)));
        return metadataCache.getList(
                userId,
                fingerprint,
                () -> buildListUncached(
                        userId,
                        parentId,
                        query,
                        type,
                        tag,
                        sort,
                        cursorToken,
                        limit));
    }

    private FileListResponse buildListUncached(
            UUID userId,
            UUID parentId,
            String query,
            String type,
            String tag,
            String sort,
            String cursorToken,
            int limit) {
        UserAccount user = userService.require(userId);
        FileEntry root = requireRoot(userId);
        FileEntry current = parentId == null || parentId.equals(root.id())
                ? null
                : requireActiveFolder(userId, parentId);
        UUID effectiveParent = current == null ? root.id() : current.id();
        String normalizedSort = normalizeSort(sort);
        FilePageCursor cursor = FilePageCursor.decode(
                cursorToken,
                normalizedSort).orElse(null);
        if (cursorToken != null && !cursorToken.isBlank() && cursor == null) {
            throw ApiException.badRequest("INVALID_CURSOR", "分页游标无效，请刷新列表");
        }
        int pageSize = Math.min(Math.max(limit, 1), 200);
        List<FileEntry> page = fileRepository.findPage(userId, new FilePageQuery(
                userId,
                effectiveParent,
                normalizeQuery(query),
                normalizeType(type),
                normalizeTag(tag),
                normalizedSort,
                cursor,
                false,
                pageSize + 1));
        boolean hasMore = page.size() > pageSize;
        List<FileEntry> entries = hasMore
                ? List.copyOf(page.subList(0, pageSize))
                : page;
        Map<UUID, Long> childCounts = childCounts(
                userId,
                entries.stream()
                        .filter(FileEntry::isFolder)
                        .map(FileEntry::id)
                        .toList());
        List<UUID> visibleIds = new ArrayList<>(
                entries.stream().map(FileEntry::id).toList());
        if (current != null) {
            visibleIds.add(current.id());
        }
        Set<UUID> sharedIds = fileShareStatus.activeSharedEntryIds(
                userId,
                visibleIds);
        String ownerName = user.displayName();
        List<FileView> views = entries.stream()
                .map(entry -> FileView.from(
                        entry,
                        ownerName,
                        childCounts.getOrDefault(entry.id(), 0L),
                        sharedIds.contains(entry.id())))
                .toList();
        List<FileView> breadcrumbViews = current == null
                ? List.of()
                : fileRepository.findAncestors(userId, current.id()).stream()
                        .map(entry -> FileView.from(entry, ownerName, 0, false))
                        .toList();
        FileEntry lastEntry = entries.isEmpty()
                ? null
                : entries.get(entries.size() - 1);
        String nextCursor = hasMore && lastEntry != null
                ? FilePageCursor.encode(
                        lastEntry.isFolder() ? 0 : 1,
                        normalizedSort,
                        lastEntry)
                : null;
        FileStorageStats storageStats = fileRepository.storageStats(userId);
        return new FileListResponse(
                views,
                current == null
                        ? null
                        : FileView.from(
                                current,
                                ownerName,
                                childCounts.getOrDefault(current.id(), 0L),
                                sharedIds.contains(current.id())),
                breadcrumbViews,
                root.id(),
                storageStats.usedBytes(),
                user.storageQuotaBytes(),
                storageStats.fileCount(),
                Math.max(0, storageStats.folderCount() - 1),
                hasMore,
                nextCursor);
    }

    public List<FileView> recent(UUID userId, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 200);
        return metadataCache.getRecent(userId, pageSize, () -> {
            UserAccount user = userService.require(userId);
            List<FileEntry> entries = fileRepository.findRecent(userId, pageSize);
            Set<UUID> sharedIds = fileShareStatus.activeSharedEntryIds(
                    userId,
                    entries.stream().map(FileEntry::id).toList());
            return entries.stream()
                    .map(entry -> FileView.from(
                            entry,
                            user.displayName(),
                            0,
                            sharedIds.contains(entry.id())))
                    .toList();
        });
    }

    public List<FileView> starred(UUID userId) {
        return metadataCache.getStarred(userId, () -> {
            UserAccount user = userService.require(userId);
            List<FileEntry> entries = fileRepository.findStarred(userId);
            Set<UUID> sharedIds = fileShareStatus.activeSharedEntryIds(
                    userId,
                    entries.stream().map(FileEntry::id).toList());
            return entries.stream()
                    .map(entry -> FileView.from(
                            entry,
                            user.displayName(),
                            0,
                            sharedIds.contains(entry.id())))
                    .toList();
        });
    }

    public List<FileTreeNode> tree(UUID userId, UUID parentId) {
        return metadataCache.getTree(
                userId,
                parentId,
                () -> buildTreeUncached(userId, parentId));
    }

    private List<FileTreeNode> buildTreeUncached(UUID userId, UUID parentId) {
        FileEntry root = requireRoot(userId);
        FileEntry current = parentId == null || parentId.equals(root.id())
                ? root
                : requireActiveFolder(userId, parentId);
        List<FileEntry> folders = fileRepository.findPage(userId, new FilePageQuery(
                userId,
                current.id(),
                null,
                "folder",
                null,
                "name-asc",
                null,
                false,
                501));
        Map<UUID, Long> counts = childCounts(
                userId,
                folders.stream().map(FileEntry::id).toList());
        return folders.stream()
                .map(entry -> new FileTreeNode(
                        entry.id(),
                        entry.name(),
                        "folder",
                        entry.parentId(),
                        counts.getOrDefault(entry.id(), 0L) > 0,
                        List.of()))
                .toList();
    }

    public FileView details(UUID userId, UUID id) {
        return metadataCache.getDetails(
                userId,
                id,
                () -> buildDetailsUncached(userId, id));
    }

    private FileView buildDetailsUncached(UUID userId, UUID id) {
        UserAccount user = userService.require(userId);
        FileEntry entry = fileRepository.findById(userId, id)
                .orElseThrow(() -> ApiException.notFound("文件不存在"));
        long itemCount = entry.isFolder()
                ? fileRepository.countChildren(userId, entry.id(), false)
                : 0;
        boolean shared = !fileShareStatus.activeSharedEntryIds(
                userId,
                List.of(entry.id())).isEmpty();
        return FileView.from(entry, user.displayName(), itemCount, shared);
    }

    @Transactional
    public FileView createFolder(UUID userId, CreateFolderRequest request) {
        UserAccount user = userService.require(userId);
        FileEntry target = resolveTargetFolder(userId, request.parentId());
        String name = requireSimpleName(request.name());
        UUID id = fileRepository.createFolder(userId, target.id(), name);
        activityEventPublisher.publishAfterCommit(userId, "CREATE_FOLDER", id);
        metadataCache.invalidateAfterCommit(userId);
        return details(userId, id);
    }

    @Transactional
    public FileView rename(UUID userId, UUID id, RenameRequest request) {
        FileEntry entry = requireActive(userId, id);
        if (entry.parentId() == null) {
            throw ApiException.forbidden("用户根目录不能重命名");
        }
        fileRepository.rename(userId, id, requireSimpleName(request.name()));
        activityEventPublisher.publishAfterCommit(userId, "RENAME", id);
        metadataCache.invalidateAfterCommit(userId);
        return details(userId, id);
    }

    @Transactional
    public FileView star(UUID userId, UUID id, StarRequest request) {
        requireActive(userId, id);
        fileRepository.setStarred(userId, id, request.starred());
        metadataCache.invalidateAfterCommit(userId);
        return details(userId, id);
    }

    @Transactional
    public void move(UUID userId, MoveRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return;
        }
        List<FileEntry> all = fileRepository.findAll(userId, false);
        Map<UUID, FileEntry> byId = index(all);
        FileEntry target = resolveTargetFolder(userId, request.targetParentId());
        Set<UUID> roots = normalizeRoots(request.ids(), byId);
        for (UUID id : roots) {
            FileEntry entry = byId.get(id);
            if (entry == null) continue;
            if (entry.parentId() == null) {
                throw ApiException.forbidden("用户根目录不能移动");
            }
            if (entry.isFolder() && descendantIds(entry.id(), all).contains(target.id())) {
                throw ApiException.conflict("INVALID_MOVE", "不能把文件夹移动到自身目录");
            }
            fileRepository.move(userId, id, target.id());
            activityEventPublisher.publishAfterCommit(userId, "MOVE", id);
        }
        metadataCache.invalidateAfterCommit(userId);
    }

    @Transactional
    public OperationResult copy(UUID userId, CopyRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return new OperationResult(List.of(), 0);
        }
        UserAccount user = userService.requireForUpdate(userId);
        List<FileEntry> all = fileRepository.findAll(userId, false);
        Map<UUID, FileEntry> byId = index(all);
        FileEntry target = resolveTargetFolder(userId, request.targetParentId());
        Set<UUID> roots = normalizeRoots(request.ids(), byId);
        Map<UUID, String> overrides = request.nameOverrides() == null
                ? Map.of()
                : request.nameOverrides().stream().collect(Collectors.toMap(
                        item -> item.id(),
                        item -> item.name(),
                        (first, second) -> second));
        long copyBytes = roots.stream()
                .flatMap(id -> descendantIds(id, all).stream())
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .filter(entry -> !entry.isFolder())
                .mapToLong(FileEntry::size)
                .sum();
        ensureQuota(user, all, copyBytes);
        List<UUID> createdRoots = new ArrayList<>();
        for (UUID id : roots) {
            FileEntry source = byId.get(id);
            if (source == null) continue;
            if (source.isFolder() && descendantIds(source.id(), all).contains(target.id())) {
                throw ApiException.conflict("INVALID_COPY", "不能把文件夹复制到自身目录");
            }
            createdRoots.add(copyRecursive(
                    userId,
                    user.username(),
                    source,
                    target.id(),
                    overrides.get(id),
                    all));
        }
        activityEventPublisher.publishAfterCommit(
                userId,
                "COPY",
                createdRoots.isEmpty() ? null : createdRoots.get(0));
        metadataCache.invalidateAfterCommit(userId);
        return new OperationResult(createdRoots, createdRoots.size());
    }

    @Transactional
    public void trash(UUID userId, IdsRequest request) {
        List<FileEntry> all = fileRepository.findAll(userId, false);
        Map<UUID, FileEntry> byId = index(all);
        Set<UUID> ids = normalizeRoots(request.ids(), byId);
        Instant now = Instant.now();
        for (UUID id : ids) {
            FileEntry entry = byId.get(id);
            if (entry == null || entry.parentId() == null) continue;
            for (UUID descendant : descendantIds(id, all)) {
                fileRepository.softDelete(userId, descendant, now);
            }
            activityEventPublisher.publishAfterCommit(userId, "TRASH", id);
        }
        metadataCache.invalidateAfterCommit(userId);
    }

    public List<FileView> trashList(UUID userId) {
        UserAccount user = userService.require(userId);
        List<FileEntry> all = fileRepository.findAll(userId, true);
        Map<UUID, List<FileEntry>> children = groupChildren(all);
        return all.stream()
                .filter(entry -> entry.deletedAt() != null)
                .filter(entry -> entry.parentId() == null
                        || all.stream().noneMatch(parent ->
                                parent.id().equals(entry.parentId())
                                        && parent.deletedAt() != null))
                .sorted(Comparator.comparing(FileEntry::deletedAt).reversed())
                .map(entry -> toView(entry, user.displayName(), children))
                .toList();
    }

    @Transactional
    public void restore(UUID userId, IdsRequest request) {
        List<FileEntry> all = fileRepository.findAll(userId, true);
        Map<UUID, FileEntry> byId = index(all);
        Set<UUID> roots = normalizeRoots(request.ids(), byId);
        for (UUID id : roots) {
            FileEntry entry = byId.get(id);
            if (entry == null) continue;
            for (UUID descendant : descendantIds(id, all)) {
                fileRepository.restore(userId, descendant);
            }
            activityEventPublisher.publishAfterCommit(userId, "RESTORE", id);
        }
        metadataCache.invalidateAfterCommit(userId);
    }

    @Transactional
    public void permanentDelete(UUID userId, IdsRequest request) {
        List<FileEntry> all = fileRepository.findAll(userId, true);
        Map<UUID, FileEntry> byId = index(all);
        Set<UUID> roots = normalizeRoots(request.ids(), byId);
        for (UUID rootId : roots) {
            FileEntry root = byId.get(rootId);
            if (root == null || root.parentId() == null) continue;
            Set<UUID> ids = descendantIds(rootId, all);
            for (UUID id : ids) {
                FileEntry entry = byId.get(id);
                if (entry != null && !entry.isFolder()
                        && entry.bucket() != null && entry.objectKey() != null) {
                    objectStorage.delete(entry.bucket(), entry.objectKey());
                }
            }
            fileRepository.deleteRows(userId, new ArrayList<>(ids));
            activityEventPublisher.publishAfterCommit(userId, "DELETE", null);
        }
        metadataCache.invalidateAfterCommit(userId);
    }

    @Transactional
    public FileView upload(UUID userId, UUID parentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "请选择要上传的文件");
        }
        UserAccount user = userService.requireForUpdate(userId);
        FileEntry target = resolveTargetFolder(userId, parentId);
        ensureQuota(
                user,
                fileRepository.storageStats(userId).usedBytes(),
                file.getSize());
        String fileName = safeFileName(file.getOriginalFilename());
        String contentType = FilePreviewType.contentType(
                file.getOriginalFilename(),
                safeMimeType(file.getContentType()));
        UUID id = UUID.randomUUID();
        StoredObject stored;
        try (InputStream input = file.getInputStream()) {
            stored = objectStorage.put(
                    user.username(),
                    id,
                    file.getSize(),
                    contentType,
                    input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read uploaded file", exception);
        }
        try {
            fileRepository.createFile(
                    id,
                    userId,
                    target.id(),
                    fileName,
                    stored.bucket(),
                    stored.objectKey(),
                    stored.size(),
                    stored.contentType());
            activityEventPublisher.publishAfterCommit(userId, "UPLOAD", id);
            metadataCache.invalidateAfterCommit(userId);
            return details(userId, id);
        } catch (RuntimeException exception) {
            objectStorage.delete(stored.bucket(), stored.objectKey());
            throw exception;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FileView registerCompletedUpload(
            UUID userId,
            UUID parentId,
            UUID fileId,
            String fileName,
            String bucket,
            String objectKey,
            long size,
            String contentType) {
        FileEntry target = resolveTargetFolder(userId, parentId);
        fileRepository.createFile(
                fileId,
                userId,
                target.id(),
                fileName,
                bucket,
                objectKey,
                size,
                contentType);
        activityEventPublisher.publishAfterCommit(userId, "UPLOAD", fileId);
        metadataCache.invalidateAfterCommit(userId);
        return details(userId, fileId);
    }

    public FileEntry requireActive(UUID userId, UUID id) {
        return fileRepository.findById(userId, id)
                .filter(entry -> entry.deletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("文件不存在"));
    }

    public FileEntry requireRoot(UUID userId) {
        return fileRepository.findRoot(userId)
                .orElseThrow(() -> ApiException.notFound("用户根目录不存在"));
    }

    public InputStream openObject(FileEntry entry) {
        if (entry.isFolder() || entry.bucket() == null || entry.objectKey() == null) {
            throw ApiException.badRequest("NOT_A_FILE", "该条目不是文件");
        }
        return objectStorage.get(entry.bucket(), entry.objectKey());
    }

    public void writeFolderZip(UUID userId, UUID folderId, OutputStream outputStream) {
        FileEntry folder = requireActive(userId, folderId);
        if (!folder.isFolder()) {
            throw ApiException.badRequest("NOT_A_FOLDER", "该条目不是文件夹");
        }
        List<FileEntry> all = fileRepository.findAll(userId, false);
        Map<UUID, List<FileEntry>> children = groupChildren(all);
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            zip.putNextEntry(new ZipEntry(safeZipPart(folder.name()) + "/"));
            zip.closeEntry();
            writeFolderEntries(
                    folder,
                    safeZipPart(folder.name()),
                    children,
                    zip);
            zip.finish();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create folder ZIP", exception);
        }
    }

    public Map<String, Long> storageStats(UUID userId) {
        FileStorageStats stats = fileRepository.storageStats(userId);
        return Map.of(
                "usedBytes", stats.usedBytes(),
                "fileCount", stats.fileCount(),
                "folderCount", Math.max(0, stats.folderCount() - 1));
    }

    public long childCount(UUID userId, UUID folderId) {
        return fileRepository.countChildren(userId, folderId, false);
    }

    public Map<UUID, FileEntry> activeEntries(
            UUID userId,
            List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return fileRepository.findByIds(userId, ids.stream().distinct().toList())
                .stream()
                .filter(entry -> entry.deletedAt() == null)
                .collect(Collectors.toMap(
                        FileEntry::id,
                        Function.identity(),
                        (first, second) -> second));
    }

    public Map<UUID, Long> childCounts(
            UUID userId,
            List<UUID> folderIds) {
        if (folderIds == null || folderIds.isEmpty()) {
            return Map.of();
        }
        return fileRepository.countChildrenBatch(
                        userId,
                        folderIds.stream().distinct().toList(),
                        false)
                .stream()
                .collect(Collectors.toMap(
                        FileChildCount::parentId,
                        FileChildCount::itemCount));
    }

    private FileEntry requireActiveFolder(UUID userId, UUID folderId) {
        FileEntry folder = requireActive(userId, folderId);
        if (!folder.isFolder()) {
            throw ApiException.badRequest("NOT_A_FOLDER", "当前项目不是文件夹");
        }
        return folder;
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "all";
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "folder", "document", "media", "code" -> type.toLowerCase(Locale.ROOT);
            default -> "all";
        };
    }

    private String normalizeTag(String tag) {
        return tag == null || tag.isBlank() ? null : tag.trim();
    }

    private String normalizeSort(String sort) {
        if (sort == null) {
            return "modified-desc";
        }
        return switch (sort) {
            case "name-asc", "size-desc", "modified-asc", "modified-desc" -> sort;
            default -> "modified-desc";
        };
    }

    private void writeFolderEntries(
            FileEntry folder,
            String path,
            Map<UUID, List<FileEntry>> children,
            ZipOutputStream zip) throws IOException {
        for (FileEntry child : children.getOrDefault(folder.id(), List.of())) {
            String childPath = path + "/" + safeZipPart(child.name());
            if (child.isFolder()) {
                zip.putNextEntry(new ZipEntry(childPath + "/"));
                zip.closeEntry();
                writeFolderEntries(child, childPath, children, zip);
            } else {
                zip.putNextEntry(new ZipEntry(childPath));
                try (InputStream input = openObject(child)) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private UUID copyRecursive(
            UUID userId,
            String username,
            FileEntry source,
            UUID targetParentId,
            String requestedName,
            List<FileEntry> all) {
        List<FileEntry> siblings = all.stream()
                .filter(entry -> entry.deletedAt() == null
                        && java.util.Objects.equals(entry.parentId(), targetParentId))
                .toList();
        String name = uniqueName(
                requestedName == null || requestedName.isBlank()
                        ? source.name()
                        : requestedName,
                siblings.stream().map(FileEntry::name).collect(Collectors.toSet()),
                source.isFolder());
        if (source.isFolder()) {
            UUID copiedFolderId = fileRepository.createFolder(userId, targetParentId, name);
            for (FileEntry child : all.stream()
                    .filter(entry -> entry.deletedAt() == null
                            && source.id().equals(entry.parentId()))
                    .toList()) {
                copyRecursive(userId, username, child, copiedFolderId, null, all);
            }
            return copiedFolderId;
        }

        UUID copiedFileId = UUID.randomUUID();
        try (InputStream input = openObject(source)) {
            StoredObject stored = objectStorage.put(
                    username,
                    copiedFileId,
                    source.size(),
                    source.mimeType(),
                    input);
            fileRepository.createFile(
                    copiedFileId,
                    userId,
                    targetParentId,
                    name,
                    stored.bucket(),
                    stored.objectKey(),
                    stored.size(),
                    stored.contentType());
            return copiedFileId;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot copy file content", exception);
        }
    }

    public FileEntry resolveTargetFolder(UUID userId, UUID parentId) {
        FileEntry root = requireRoot(userId);
        if (parentId == null || parentId.equals(root.id())) {
            return root;
        }
        FileEntry folder = fileRepository.findById(userId, parentId)
                .filter(entry -> entry.deletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("目标文件夹不存在"));
        if (!folder.isFolder()) {
            throw ApiException.badRequest("INVALID_TARGET", "目标不是文件夹");
        }
        return folder;
    }

    private FileEntry requireFolder(List<FileEntry> entries, UUID id) {
        return entries.stream()
                .filter(entry -> entry.id().equals(id))
                .filter(FileEntry::isFolder)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("文件夹不存在"));
    }

    public record FileTreeNode(
            UUID id,
            String name,
            String kind,
            UUID parentId,
            boolean hasChildren,
            List<FileTreeNode> children) {
    }

    private FileView toView(
            FileEntry entry,
            String ownerName,
            Map<UUID, List<FileEntry>> children) {
        long itemCount = entry.isFolder()
                ? children.getOrDefault(entry.id(), List.of()).size()
                : 0;
        return FileView.from(entry, ownerName, itemCount, false);
    }

    private Map<UUID, List<FileEntry>> groupChildren(List<FileEntry> entries) {
        Map<UUID, List<FileEntry>> grouped = new HashMap<>();
        for (FileEntry entry : entries) {
            if (entry.parentId() != null) {
                grouped.computeIfAbsent(entry.parentId(), ignored -> new ArrayList<>())
                        .add(entry);
            }
        }
        return grouped;
    }

    private Map<UUID, FileEntry> index(List<FileEntry> entries) {
        return entries.stream().collect(Collectors.toMap(
                FileEntry::id,
                Function.identity(),
                (first, second) -> second,
                LinkedHashMap::new));
    }

    private Set<UUID> normalizeRoots(List<UUID> ids, Map<UUID, FileEntry> byId) {
        if (ids == null) return Set.of();
        Set<UUID> roots = new HashSet<>(ids);
        for (UUID id : List.copyOf(roots)) {
            FileEntry cursor = byId.get(id);
            while (cursor != null && cursor.parentId() != null) {
                if (roots.contains(cursor.parentId())) {
                    roots.remove(id);
                    break;
                }
                cursor = byId.get(cursor.parentId());
            }
        }
        return roots;
    }

    private Set<UUID> descendantIds(UUID rootId, List<FileEntry> entries) {
        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        result.add(rootId);
        while (!queue.isEmpty()) {
            UUID parent = queue.removeFirst();
            for (FileEntry entry : entries) {
                if (parent.equals(entry.parentId()) && result.add(entry.id())) {
                    queue.addLast(entry.id());
                }
            }
        }
        return result;
    }

    private long usedBytes(List<FileEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.isFolder())
                .mapToLong(FileEntry::size)
                .sum();
    }

    private void ensureQuota(UserAccount user, List<FileEntry> entries, long additionalBytes) {
        ensureQuota(user, usedBytes(entries), additionalBytes);
    }

    private void ensureQuota(
            UserAccount user,
            long usedBytes,
            long additionalBytes) {
        if (additionalBytes <= 0) {
            return;
        }
        long committed;
        try {
            committed = Math.addExact(
                    usedBytes,
                    storageReservation.reservedBytes(user.id()));
        } catch (ArithmeticException exception) {
            throw ApiException.quotaExceeded(
                    "\u5b58\u50a8\u7a7a\u95f4\u4e0d\u8db3\uff0c\u65e0\u6cd5\u5b8c\u6210\u672c\u6b21\u64cd\u4f5c");
        }
        long available = Math.max(0, user.storageQuotaBytes() - committed);
        if (additionalBytes > available) {
            throw ApiException.quotaExceeded("存储空间不足，无法完成本次操作");
        }
    }

    public String safeMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        String value = contentType.trim();
        if (value.length() > 255
                || value.chars().anyMatch(Character::isISOControl)) {
            return "application/octet-stream";
        }
        try {
            return org.springframework.http.MediaType.parseMediaType(value).toString();
        } catch (RuntimeException exception) {
            return "application/octet-stream";
        }
    }

    private String requireSimpleName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank() || name.length() > 255
                || name.contains("/") || name.contains("\\")
                || name.contains("\0")) {
            throw ApiException.badRequest("INVALID_NAME", "名称不合法");
        }
        return name;
    }

    public String safeFileName(String original) {
        String name = original == null ? "unnamed" : original;
        name = name.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1);
        return requireSimpleName(name);
    }

    private String safeZipPart(String value) {
        return value.replace("/", "_").replace("\\", "_").replace("\0", "");
    }

    private String uniqueName(String requested, Set<String> existing, boolean folder) {
        if (!existing.contains(requested)) return requested;
        String extension = "";
        String base = requested;
        if (!folder) {
            int index = requested.lastIndexOf('.');
            if (index > 0) {
                extension = requested.substring(index);
                base = requested.substring(0, index);
            }
        }
        String candidate = base + " 副本" + extension;
        int index = 1;
        while (existing.contains(candidate)) {
            index += 1;
            candidate = base + " 副本 " + index + extension;
        }
        return candidate;
    }
}
