package com.filehome.upload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MultipartUploadRepository {

    int insert(@Param("session") MultipartUploadSession session);

    Optional<MultipartUploadSession> findByIdAndOwner(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId);

    Optional<MultipartUploadSession> findByIdAndOwnerForUpdate(
            @Param("id") UUID id,
            @Param("ownerId") UUID ownerId);

    List<MultipartUploadSession> findActiveByOwner(@Param("ownerId") UUID ownerId);

    int countActiveByOwner(@Param("ownerId") UUID ownerId);

    long sumReservedBytes(@Param("ownerId") UUID ownerId);

    int upsertPart(@Param("part") MultipartUploadPart part);

    List<MultipartUploadPart> findParts(@Param("sessionId") UUID sessionId);

    int markUploading(@Param("id") UUID id);

    int markCompleting(@Param("id") UUID id);

    int markCompleted(@Param("id") UUID id);

    int markAborted(@Param("id") UUID id);

    List<MultipartUploadSession> findExpired(@Param("limit") int limit);

    int deleteTerminalBefore();
}
