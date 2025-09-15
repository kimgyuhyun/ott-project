package com.ottproject.ottbackend.mybatis;

import com.ottproject.ottbackend.dto.BingeWatchDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 정주행 관련 MyBatis 매퍼
 *
 * 큰 흐름
 * - 사용자의 정주행 완료 작품을 조회한다.
 * - 완결 작품 중 모든 에피소드를 90% 이상 시청한 작품을 정주행으로 간주한다.
 *
 * 메서드 개요
 * - findBingeWatchedAnimes: 사용자별 정주행 완료 작품 목록
 */
@Mapper
public interface BingeWatchMapper {
    
    /**
     * 사용자별 정주행 완료 작품 목록 조회
     * 
     * @param userId 사용자 ID
     * @return 정주행 완료 작품 목록
     */
    List<BingeWatchDto> findBingeWatchedAnimes(@Param("userId") Long userId);
}
