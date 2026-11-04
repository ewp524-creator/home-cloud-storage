package com.filehome.share;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShareRepository {

    int insert(@Param("share") ShareLink share);

    Optional<ShareLink> findById(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id);

    Optional<ShareLink> findByTokenHash(@Param("tokenHash") byte[] tokenHash);

    Optional<ShareLink> findByOwnerAndRoot(
            @Param("ownerId") UUID ownerId,
            @Param("rootEntryId") UUID rootEntryId);

    List<ShareLink> findAllByOwner(@Param("ownerId") UUID ownerId);

    List<ShareLink> findActiveByOwner(@Param("ownerId") UUID ownerId);

    List<UUID> findActiveSharedRootIds(
            @Param("ownerId") UUID ownerId,
            @Param("entryIds") List<UUID> entryIds);

    int updateShare(@Param("share") ShareLink share);

    int revoke(
            @Param("ownerId") UUID ownerId,
            @Param("id") UUID id);

    int log(
            @Param("shareId") UUID shareId,
            @Param("viewerUserId") UUID viewerUserId,
            @Param("action") String action,
            @Param("succeeded") boolean succeeded);

    long count(
            @Param("shareId") UUID shareId,
            @Param("action") String action,
            @Param("onlySucceeded") boolean onlySucceeded);

    List<ShareAccessCount> findAccessCounts(@Param("ownerId") UUID ownerId);
}
