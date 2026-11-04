package com.filehome.activity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActivityLogRepository {

    int append(@Param("event") ActivityEvent event);
}
