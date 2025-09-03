package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.EpisodeCommentsResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * EpisodeCommentQueryMapper
 *
 * 큰 흐름
 * - 에피소드 댓글 조회 전용 쿼리를 담당하는 MyBatis 매퍼.
 *
 * 메서드 개요
 * - findCommentsByEpisodeId/countCommentsByEpisodeId: 에피소드별 댓글 목록/총 개수
 * - findRepliesByParentId: 부모 댓글 기준 대댓글 목록
 */
@Mapper
public interface EpisodeCommentQueryMapper {

    // 댓글 목록: 특정 에피소드(episodeId) 기준(최상위 댓글만)
    List<EpisodeCommentsResponseDto> findCommentsByEpisodeId(
            @Param("episodeId") Long episodeId, // 대상 에피소드 ID
            @Param("currentUserId") Long currentUserId, // 현재 사용자 ID
            @Param("sort") String sort, // 정렬 latest|best
            @Param("limit") int limit, // 페이지 크기
            @Param("offset") int offset // 오프셋
    );

    // 댓글 총 개수
    long countCommentsByEpisodeId(@Param("episodeId") Long episodeId);

    // 대댓글 목록: 특정 부모(parentId) 기준
    List<EpisodeCommentsResponseDto> findRepliesByParentId(
            @Param("parentId") Long parentId, // 부모 댓글 ID
            @Param("currentUserId") Long currentUserId // 현재 사용자 ID
    );
}
