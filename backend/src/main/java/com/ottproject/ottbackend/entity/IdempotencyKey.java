package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.IdempotencyKeyStatus;
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
        @Index(name = "ux_idempotency_key", columnList = "key_value", unique = true),
        @Index(name = "idx_idempotency_keys_purpose_status_created", columnList = "purpose, status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @Column(name = "key_value", nullable = false, unique = true, length = 191)
    private String keyValue; // 토큰 값

    @Column(nullable = false)
    private String purpose; // 용도(e.g., membership.cancel)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IdempotencyKeyStatus status; // 선점 상태

    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 멱등성 키 생성 (비즈니스 로직 캡슐화)
     * 
     * @param key 키 값
     * @param requestType 요청 유형
     * @param response 응답 데이터
     * @param createdAt 생성 시각(엔티티는 현재 시각을 스스로 읽지 않는다)
     * @return 생성된 IdempotencyKey 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static IdempotencyKey createIdempotencyKey(String key, String requestType, String response,
                                                      LocalDateTime createdAt) {
        // 필수 필드 검증
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("키는 필수입니다.");
        }
        if (requestType == null || requestType.trim().isEmpty()) {
            throw new IllegalArgumentException("요청 유형은 필수입니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("생성 시각은 필수입니다.");
        }
        // response 는 검증하지 않는다: 이 엔티티에는 응답 컬럼이 없어 값이 저장되지 않고,
        // 모든 호출처(웹훅/체크아웃/멤버십 해지)가 null 또는 "" 를 넘긴다.
        // 과거에 필수 검증이 있어 eventId 가 달린 웹훅 처리마다 IllegalArgumentException 으로
        // 멱등키 저장이 실패(→ PG 재전송 반복)했다.

        // IdempotencyKey 엔티티 생성
        IdempotencyKey idempotencyKey = new IdempotencyKey();
        idempotencyKey.keyValue = key.trim();
        idempotencyKey.purpose = requestType.trim();
        // 키 삽입과 실제 작업이 한 트랜잭션인 호출처(체크아웃/웹훅/멤버십 해지)는 작업이 실패하면 키도 함께
        // 롤백되므로, 저장된 키는 곧 확정된 작업이다. 대사 배치가 그런 키를 건드리지 않도록 CONFIRMED 로 둔다.
        idempotencyKey.status = IdempotencyKeyStatus.CONFIRMED;
        idempotencyKey.createdAt = createdAt;

        return idempotencyKey;
    }

    /**
     * 선점 상태(CLAIMED) 멱등키 생성 — 외부 호출 전에 키를 커밋하는 경로 전용.
     * - 환불처럼 키 삽입을 커밋한 뒤에 게이트웨이를 부르는 경로는, 그 호출이 실제로 나갔는지 아직 모른다.
     *   대사 배치가 역조회해 판정할 수 있도록 확정과 구분해 둔다.
     */
    public static IdempotencyKey createClaimedIdempotencyKey(String key, String requestType, LocalDateTime createdAt) {
        IdempotencyKey idempotencyKey = createIdempotencyKey(key, requestType, null, createdAt);
        idempotencyKey.status = IdempotencyKeyStatus.CLAIMED;
        return idempotencyKey;
    }

    /**
     * 선점을 확정으로 전이한다(멱등).
     */
    public void markConfirmed() {
        this.status = IdempotencyKeyStatus.CONFIRMED;
    }
}


