package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 아이드엠포턴시 키 엔티티
 *
 * 큰 흐름
 * - 결제/해지/환불/웹훅 등 민감 작업의 멱등 처리를 보장한다.
 * - 키 값은 고유 인덱스로 중복을 차단한다.
 *
 * 필드 개요
 * - id/keyValue/purpose/createdAt: 식별/토큰/용도/생성 시각
 */
@Entity
@Table(name = "idempotency_keys", indexes = {
        @Index(name = "ux_idempotency_key", columnList = "key_value", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @Column(name = "key_value", nullable = false, unique = true, length = 191)
    private String keyValue; // 토큰 값

    @Column(nullable = false)
    private String purpose; // 용도(e.g., membership.cancel)

    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 멱등성 키 생성 (비즈니스 로직 캡슐화)
     * 
     * @param key 키 값
     * @param requestType 요청 유형
     * @param response 응답 데이터
     * @return 생성된 IdempotencyKey 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static IdempotencyKey createIdempotencyKey(String key, String requestType, String response) {
        // 필수 필드 검증
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("키는 필수입니다.");
        }
        if (requestType == null || requestType.trim().isEmpty()) {
            throw new IllegalArgumentException("요청 유형은 필수입니다.");
        }
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("응답 데이터는 필수입니다.");
        }

        // IdempotencyKey 엔티티 생성
        IdempotencyKey idempotencyKey = new IdempotencyKey();
        idempotencyKey.keyValue = key.trim();
        idempotencyKey.purpose = requestType.trim();
        idempotencyKey.createdAt = LocalDateTime.now();

        return idempotencyKey;
    }
}


