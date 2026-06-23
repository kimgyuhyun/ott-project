package com.ottproject.ottbackend.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ClientRequestUtil
 *
 * 큰 흐름
 * - HTTP 요청에서 감사 로그에 필요한 클라이언트 메타데이터(IP/User-Agent)를 안전하게 추출한다.
 * - nginx 등 리버스 프록시 뒤에 있는 환경을 고려해 X-Forwarded-For / X-Real-IP 헤더를 우선 확인한다.
 *
 * 메서드 개요
 * - clientIp: 실제 클라이언트 IP 추출(프록시 헤더 우선)
 * - userAgent: User-Agent 추출(컬럼 길이에 맞춰 절단)
 */
public final class ClientRequestUtil {

    private ClientRequestUtil() { // 유틸 클래스: 인스턴스화 방지
    }

    /**
     * 실제 클라이언트 IP 추출
     * - 프록시 체인을 고려해 X-Forwarded-For 의 첫 번째 값을 우선 사용한다.
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For"); // 프록시가 전달한 원 IP 체인
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(','); // "client, proxy1, proxy2" 형태에서 맨 앞이 원 클라이언트
            return (comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
        }
        String realIp = request.getHeader("X-Real-IP"); // nginx 단일 프록시 환경
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr(); // 프록시 헤더가 없으면 소켓 원격 주소
    }

    /**
     * User-Agent 추출 (DB 컬럼 길이 512 에 맞춰 절단)
     */
    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return null;
        }
        return ua.length() > 512 ? ua.substring(0, 512) : ua; // 컬럼 길이 초과 방지
    }
}
