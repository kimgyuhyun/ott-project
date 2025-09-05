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

    @Column(name = "is_active", nullable = false) // 스키마 컬럼명 명시
    private Boolean isActive = true; // 플랜 활성화 여부

    // ===== 정적 팩토리 메서드 =====

    /**
     * 기본 플랜 생성 (비즈니스 로직 캡슐화)
     * 
     * @param name 플랜명 (고유해야 함)
     * @param description 플랜 설명
     * @param price 월 가격 (0 이상)
     * @param durationMonths 기간 (1개월 이상)
     * @return 생성된 MembershipPlan 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static MembershipPlan createBasicPlan(String name, String description, Money price, Integer durationMonths) {
        // 필수 필드 검증
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜명은 필수입니다.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜 설명은 필수입니다.");
        }
        if (price == null || price.getAmount() < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
        if (durationMonths == null || durationMonths < 1) {
            throw new IllegalArgumentException("기간은 1개월 이상이어야 합니다.");
        }

        // MembershipPlan 엔티티 생성
        MembershipPlan plan = new MembershipPlan();
        plan.code = generateCode(name);
        plan.name = name.trim();
        plan.maxQuality = "720p"; // 기본 화질
        plan.price = price;
        plan.periodMonths = durationMonths;
        plan.concurrentStreams = 1; // 기본 동시접속 수
        plan.isActive = true;

        return plan;
    }

    /**
     * 프리미엄 플랜 생성 (비즈니스 로직 캡슐화)
     * 
     * @param name 플랜명 (고유해야 함)
     * @param description 플랜 설명
     * @param price 월 가격 (0 이상)
     * @param durationMonths 기간 (1개월 이상)
     * @param features 추가 기능 목록
     * @return 생성된 MembershipPlan 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static MembershipPlan createPremiumPlan(String name, String description, Money price, 
                                                  Integer durationMonths, String features) {
        // 필수 필드 검증
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜명은 필수입니다.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜 설명은 필수입니다.");
        }
        if (price == null || price.getAmount() < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
        if (durationMonths == null || durationMonths < 1) {
            throw new IllegalArgumentException("기간은 1개월 이상이어야 합니다.");
        }

        // MembershipPlan 엔티티 생성
        MembershipPlan plan = new MembershipPlan();
        plan.code = generateCode(name);
        plan.name = name.trim();
        plan.maxQuality = "1080p"; // 프리미엄 화질
        plan.price = price;
        plan.periodMonths = durationMonths;
        plan.concurrentStreams = 3; // 프리미엄 동시접속 수
        plan.isActive = true;

        return plan;
    }

    /**
     * 무료 체험 플랜 생성 (비즈니스 로직 캡슐화)
     * 
     * @param name 플랜명 (고유해야 함)
     * @param description 플랜 설명
     * @param trialDays 체험 기간 (일 단위)
     * @return 생성된 MembershipPlan 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static MembershipPlan createTrialPlan(String name, String description, Integer trialDays) {
        // 필수 필드 검증
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜명은 필수입니다.");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜 설명은 필수입니다.");
        }
        if (trialDays == null || trialDays < 1) {
            throw new IllegalArgumentException("체험 기간은 1일 이상이어야 합니다.");
        }

        // MembershipPlan 엔티티 생성
        MembershipPlan plan = new MembershipPlan();
        plan.code = generateCode(name);
        plan.name = name.trim();
        plan.maxQuality = "720p"; // 체험 화질
        plan.price = new Money(0L, "KRW"); // 무료
        plan.periodMonths = 1; // 1개월
        plan.concurrentStreams = 1; // 체험 동시접속 수
        plan.isActive = true;

        return plan;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 플랜 비활성화
     * @throws IllegalStateException 이미 비활성화된 플랜인 경우
     */
    public void deactivate() {
        if (!this.isActive) {
            throw new IllegalStateException("이미 비활성화된 플랜입니다.");
        }

        this.isActive = false;
    }

    /**
     * 플랜 활성화
     * @throws IllegalStateException 이미 활성화된 플랜인 경우
     */
    public void activate() {
        if (this.isActive) {
            throw new IllegalStateException("이미 활성화된 플랜입니다.");
        }

        this.isActive = true;
    }

    /**
     * 플랜명 변경
     * @param newName 새로운 플랜명
     * @throws IllegalArgumentException 플랜명이 유효하지 않은 경우
     */
    public void updateName(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜명은 필수입니다.");
        }

        this.name = newName.trim();
        this.code = generateCode(newName);
    }

    /**
     * 가격 변경
     * @param newPrice 새로운 가격
     * @throws IllegalArgumentException 가격이 유효하지 않은 경우
     */
    public void updatePrice(Money newPrice) {
        if (newPrice == null || newPrice.getAmount() < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }

        this.price = newPrice;
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 플랜명에서 코드 생성
     * @param name 플랜명
     * @return 생성된 코드
     */
    private static String generateCode(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("플랜명은 필수입니다.");
        }

        return name.trim().toUpperCase().replaceAll("\\s+", "_");
    }
}


