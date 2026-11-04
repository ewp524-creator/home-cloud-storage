package com.filehome.file;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileRepository {

    Optional<FileEntry> findRoot(@Param("ownerId") UUID ownerId);

    Optional<FileEntry> findById(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id);

    List<FileEntry> findAll(
            @Param("ownerId") UUID ownerId,
            @Param("includeDeleted") boolean includeDeleted);

    List<FileEntry> findPage(
            @Param("ownerId") UUID ownerId,
            @Param("query") FilePageQuery query);

    List<FileEntry> findRecent(
            @Param("ownerId") UUID ownerId,
            @Param("limit") int limit);

    List<FileEntry> findStarred(@Param("ownerId") UUID ownerId);

    List<FileEntry> findAncestors(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id);

    List<FileEntry> findByIds(
            @Param("ownerId") UUID ownerId,
            @Param("ids") List<UUID> ids);

    long countChildren(
            @Param("ownerId") UUID ownerId,
            @Param("parentId") UUID parentId,
            @Param("includeDeleted") boolean includeDeleted);

    int countActiveChildByName(
            @Param("ownerId") UUID ownerId,
            @Param("parentId") UUID parentId,
            @Param("name") String name);

    List<FileChildCount> countChildrenBatch(
            @Param("ownerId") UUID ownerId,
            @Param("parentIds") List<UUID> parentIds,
            @Param("includeDeleted") boolean includeDeleted);

    FileStorageStats storageStats(@Param("ownerId") UUID ownerId);

    int insertEntry(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId,
            @Param("parentId") UUID parentId,
            @Param("kind") FileEntry.EntryKind kind,
            @Param("name") String name);

    int insertObject(
            @Param("id") UUID id,
            @Param("bucket") String bucket,
            @Param("objectKey") String objectKey,
            @Param("size") long size,
            @Param("mimeType") String mimeType);

    int rename(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id,
            @Param("name") String name);

    int setStarred(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id,
            @Param("starred") boolean starred);

    int move(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id,
            @Param("parentId") UUID parentId);

    int softDelete(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id,
            @Param("deletedAt") Instant deletedAt);

    int restore(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id);

    int deleteRows(
            @Param("ownerId") UUID ownerId,
            @Param("ids") List<UUID> ids);

    default UUID createRoot(UUID ownerId, String username) {
        UUID id = UUID.randomUUID();
        insertEntry(id, ownerId, null, FileEntry.EntryKind.FOLDER, username);
        return id;
    }

    default UUID createFolder(UUID ownerId, UUID parentId, String name) {
        UUID id = UUID.randomUUID();
        insertEntry(id, ownerId, parentId, FileEntry.EntryKind.FOLDER, name);
        return id;
    }

    default void createFile(
            UUID id,
            UUID ownerId,
            UUID parentId,
            String name,
            String bucket,
            String objectKey,
            long size,
            String mimeType) {
        insertEntry(id, ownerId, parentId, FileEntry.EntryKind.FILE, name);
        insertObject(id, bucket, objectKey, size, mimeType);
    }
}
