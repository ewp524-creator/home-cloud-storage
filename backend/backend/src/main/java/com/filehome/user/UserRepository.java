package com.filehome.user;

import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRepository {

    Optional<UserAccount> findById(@Param("id") UUID id);

    Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

    Optional<UserAccount> findByAccount(@Param("account") String account);

    Optional<UserAccount> findByUsername(@Param("username") String username);

    Optional<UserAccount> findByEmail(@Param("email") String email);

    int insert(@Param("account") UserAccount account);

    int updateAvatarUrl(
            @Param("id") UUID id,
            @Param("avatarUrl") String avatarUrl);

    int updateProfile(
            @Param("id") UUID id,
            @Param("email") String email,
            @Param("displayName") String displayName);

    int updatePasswordHash(
            @Param("id") UUID id,
            @Param("passwordHash") String passwordHash);

    int deactivate(@Param("id") UUID id);
}
