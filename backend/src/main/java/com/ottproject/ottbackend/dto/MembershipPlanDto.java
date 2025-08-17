package com.ottproject.ottbackend.dto;

/**
 * 플랜 목록 노출 DTO
 * - 플랜ID/이름/월가격/기간/동시접속/품질
 */
public class MembershipPlanDto {
    public Long id; // 플랜 id
    public String name; // 플랜 이름
    public Integer monthlyPrice; // 월 가격(KRW, VAT 포함)
    public Integer periodMonths; // 기간(월)
    public Integer concurrentStreams; // 동시 접속
    public String maxQuality; // 화질
}
