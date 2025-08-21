package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 구독 플랜 엔티티
 *
 * 큰 흐름
 * - 멤버십의 요금제 정보(코드/이름/가격/기간/동시접속/최대 화질)를 보관한다.
 * - 가격은 `Money` VO(@Embeddable)로 임베드하여 금액/통화 일관성을 보장한다.
 * - 애플리케이션에서는 DTO로 노출하며, 플랜 변경/조회에 활용된다.
 *
 * 필드 개요
 * - id: PK
 * - code: 플랜 고유 코드(FREE/BASIC/PREMIUM 등)
 * - name: 표기명
 * - maxQuality: 허용 최대 화질(예: 720p/1080p)
 * - price: VO 금액/통화(월요금, VAT 포함)
 * - periodMonths: 청구 주기(월), 1=월간/12=연간 등
 * - concurrentStreams: 동시접속 허용 수
 */
@Entity
@Table(name = "plans") // 테이블명 매핑
@Getter // 게터 생성
@Setter // 세터 생성
@Builder // 빌더 생성
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
public class MembershipPlan { // 멤버쉽 플랜
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) // pk 자동 증가
    private Long id; // PK

    @Column(nullable = false, unique = true) // 고유 코드 제약
    private String code; // e.g., FREE, BASIC, PREMIUM

    @Column(nullable = false) // null 불가
    private String name; // 표기명

    @Column(nullable = false) // null 불가
    private String maxQuality; // "720p" / "1080p"

    @Embedded // VO 임베드
    @AttributeOverrides({ // 컬럼명 재정의로 기존 스키마 호환
        @AttributeOverride(name = "amount", column = @Column(name = "price_monthly_vat_included", nullable = false)), // 금액 컬럼
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency", length = 3, nullable = false)) // 통화 컬럼
    })
    private Money price; // 월 가격(VO: 금액/통화)

    @Column(nullable = false) // null 불가
    private Integer periodMonths; // 청구주기 (월 단위, 1~월간, 12~연간 등)

    @Column(nullable = false) // null 불가
    private Integer concurrentStreams; // 동시접속 허용 수
}


