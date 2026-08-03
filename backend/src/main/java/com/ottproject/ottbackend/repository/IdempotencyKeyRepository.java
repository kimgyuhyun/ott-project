package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.IdempotencyKey;
import com.ottproject.ottbackend.enums.IdempotencyKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * IdempotencyKeyRepository
 *
 * 큰 흐름
 * - 멱등 키를 저장/조회하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByKeyValue: 키 문자열로 단건 조회
 * - findByPurposeAndStatusAndCreatedAtBefore: 대사 대상(오래된 선점) 조회
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
	Optional<IdempotencyKey> findByKeyValue(String keyValue);

	/**
	 * 오래된 선점 키 조회(대사 배치용)
	 * - IdempotencyKey 에는 updatedAt 이 없어 경과 시간 기준은 createdAt 뿐이다.
	 */
	List<IdempotencyKey> findByPurposeAndStatusAndCreatedAtBefore(
			String purpose, IdempotencyKeyStatus status, LocalDateTime createdAtBefore);
}


