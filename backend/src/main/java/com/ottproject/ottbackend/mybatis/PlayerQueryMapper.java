package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.RecentAnimeWatchDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

/**
 * PlayerQueryMapper
 *
 * 큰 흐름
 * - 마이페이지 등 읽기 전용(조회) 쿼리를 담당하는 MyBatis 매퍼.
 */
@Mapper
public interface PlayerQueryMapper {

    /**
     * 사용자별로 애니당 최신 1개 시청 기록을 최신순으로 반환
     */
    List<RecentAnimeWatchDto> findRecentAnimeByUser(
            @Param("userId") Long userId,
            @Param("limit") int limit,
            @Param("offset") int offset,
            @Param("cursorUpdatedAt") LocalDateTime cursorUpdatedAt,
            @Param("cursorAnimeId") Long cursorAnimeId
    );
}


