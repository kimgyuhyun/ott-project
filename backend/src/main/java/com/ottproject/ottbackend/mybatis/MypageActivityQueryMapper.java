package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.MyCommentItemDto;
import com.ottproject.ottbackend.dto.MyRatingItemDto;
import com.ottproject.ottbackend.dto.MyReviewItemDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MypageActivityQueryMapper {
    List<MyRatingItemDto> findMyRatings(
            @Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    List<MyReviewItemDto> findMyReviews(
            @Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    List<MyCommentItemDto> findMyComments(
            @Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);
}
