package com.ottproject.ottbackend.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RatingQueryMapper {

    Double findUserRatingByAnimeId(@Param("userId") Long userId, @Param("aniId") Long aniId);

    // 반환: map {rating: Integer, count: Integer}
    List<Map<String, Object>> findRatingDistributionByAnimeId(@Param("aniId") Long aniId);

    Double findAverageRatingByAnimeId(@Param("aniId") Long aniId);

    Long countRatingsByAnimeId(@Param("aniId") Long aniId);

    interface RatingDistributionRow {
        Integer getRating();
        Integer getCount();
    }
}


