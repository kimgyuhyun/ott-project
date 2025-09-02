package com.ottproject.ottbackend.dto;

/**
 * 플랜 목록 노출 DTO
 *
 * 큰 흐름
 * - 요금제 비교/노출에 필요한 최소 필드를 담는다.
 * - 서버 엔티티(`MembershipPlan`)에서 뷰 용도로 변환되어 전달된다.
 *
 * 필드 개요
 * - id/code/name: 식별/코드/표기명
 * - monthlyPrice/periodMonths: 월 가격/기간(월)
 * - concurrentStreams/maxQuality: 동시접속/최대 화질
 */
public class MembershipPlanDto {
    public Long id; // 플랜 id
    public String code; // 플랜 코드
    public String name; // 플랜 이름
    public Integer monthlyPrice; // 월 가격(KRW, VAT 포함)
    public Integer periodMonths; // 기간(월)
    public Integer concurrentStreams; // 동시 접속
    public String maxQuality; // 화질
}
