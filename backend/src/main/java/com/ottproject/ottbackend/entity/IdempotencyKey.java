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
@Builder
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
}


