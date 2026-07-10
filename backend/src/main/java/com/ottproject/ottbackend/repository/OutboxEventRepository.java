package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.OutboxEvent;
import com.ottproject.ottbackend.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 아웃박스 이벤트 리포지토리
 *
 * 큰 흐름
 * - 발행 대기(NEW) 이벤트를 오래된 순으로 조회하여 발행기가 배치 발행한다.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 발행 대기(NEW) 이벤트를 생성 순으로 조회(배치 크기는 Pageable 로 제한)
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
