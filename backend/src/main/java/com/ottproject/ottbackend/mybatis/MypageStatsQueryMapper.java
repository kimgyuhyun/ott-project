package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.MypageStatsDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MypageStatsQueryMapper {
    MypageStatsDto findMypageStatsByUserId(@Param("userId") Long userId);
}


