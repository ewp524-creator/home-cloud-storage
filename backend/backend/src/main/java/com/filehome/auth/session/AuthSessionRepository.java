package com.filehome.auth.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthSessionRepository {

    int insert(@Param("session") AuthSession session);

    Optional<AuthSessionAccountRow> findActiveByTokenHash(
            @Param("tokenHash") byte[] tokenHash);

    int revokeByTokenHash(@Param("tokenHash") byte[] tokenHash);

    int touchIfStale(@Param("id") UUID id);

    int revokeByIdAndUser(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    Optional<AuthSessionToken> findActiveTokenByIdAndUser(
            @Param("id") UUID id,
            @Param("userId") UUID userId);

    int revokeOthers(
            @Param("userId") UUID userId,
            @Param("currentSessionId") UUID currentSessionId);

    int revokeAllByUser(@Param("userId") UUID userId);

    List<AuthSessionToken> findActiveTokensByUserExcept(
            @Param("userId") UUID userId,
            @Param("currentSessionId") UUID currentSessionId);

    List<AuthSessionToken> findActiveTokensByUser(
            @Param("userId") UUID userId);

    List<AuthSessionView> listActive(
            @Param("userId") UUID userId,
            @Param("currentSessionId") UUID currentSessionId);

    int deleteExpired(@Param("limit") int limit);
}
