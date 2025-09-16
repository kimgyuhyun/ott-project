package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MypageStatsDto;
import com.ottproject.ottbackend.dto.MyRatingItemDto;
import com.ottproject.ottbackend.dto.MyReviewItemDto;
import com.ottproject.ottbackend.dto.MyCommentItemDto;
import com.ottproject.ottbackend.mybatis.MypageStatsQueryMapper;
import com.ottproject.ottbackend.mybatis.MypageActivityQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MypageService {

    private final MypageStatsQueryMapper mypageStatsQueryMapper;
    private final MypageActivityQueryMapper mypageActivityQueryMapper;

    @Transactional(readOnly = true)
    public MypageStatsDto getMypageStats(Long userId) {
        return mypageStatsQueryMapper.findMypageStatsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public java.util.List<MyRatingItemDto> getMyRatings(Long userId, int page, int size) {
        int offset = page * size;
        return mypageActivityQueryMapper.findMyRatings(userId, offset, size);
    }

    @Transactional(readOnly = true)
    public java.util.List<MyReviewItemDto> getMyReviews(Long userId, int page, int size) {
        int offset = page * size;
        return mypageActivityQueryMapper.findMyReviews(userId, offset, size);
    }

    @Transactional(readOnly = true)
    public java.util.List<MyCommentItemDto> getMyComments(Long userId, int page, int size) {
        int offset = page * size;
        return mypageActivityQueryMapper.findMyComments(userId, offset, size);
    }
}


