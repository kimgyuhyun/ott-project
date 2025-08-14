package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.*;
import org.apache.ibatis.annotations.Mapper; // MyBatis 매퍼 애노테이션
import org.apache.ibatis.annotations.Param;  // MyBatis 파라미터 바인딩

import java.util.List;

@Mapper // MyBatis 매퍼
public interface FavoriteQueryMapper { // 찜 목록 전용 매퍼
    List<FavoriteAniDto> findFavoriteAnimesByUser( // 유저별 찜 목록 조회
                                                   @Param("userId") Long userId, // 사용자 ID
                                                   @Param("sort") String sort, // 정렬키: favoritedAt|title|popular
                                                   @Param("limit") int limit, // 페이지 크기
                                                   @Param("offset") int offset // 오프셋
    ); // 메서드 끝

    long countFavoriteAnimesByUser( // 총 개수 조회
                                    @Param("userId") Long userId // 사용자 ID
    ); // 메서드 끝
}