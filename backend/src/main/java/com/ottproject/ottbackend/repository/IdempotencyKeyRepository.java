package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * IdempotencyKeyRepository
 *
 * 큰 흐름
 * - 멱등 키를 저장/조회하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByKeyValue: 키 문자열로 단건 조회
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
	Optional<IdempotencyKey> findByKeyValue(String keyValue);
}


