package com.filehome.user;

import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPreferenceRepository {

    Optional<UserPreference> findById(@Param("userId") UUID userId);

    int upsert(@Param("preference") UserPreference preference);
}
