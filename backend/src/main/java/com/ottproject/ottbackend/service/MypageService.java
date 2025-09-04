package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.MypageStatsDto;
import com.ottproject.ottbackend.mybatis.MypageStatsQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MypageService {

    private final MypageStatsQueryMapper mypageStatsQueryMapper;

    @Transactional(readOnly = true)
    public MypageStatsDto getMypageStats(Long userId) {
        return mypageStatsQueryMapper.findMypageStatsByUserId(userId);
    }
}


