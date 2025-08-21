package com.ottproject.ottbackend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * HlsSignedUrlUtil
 *
 * 큰 흐름
 * - Nginx secure_link용 서명값(st)과 만료 시각(e)을 생성한다.
 *
 * 메서드 개요
 * - generateSignature: MD5(expires + uriPath + " " + secret) → Base64
 * - defaultExpiryFromNowSeconds: 현재 시각 기준 TTL초 뒤 만료 epoch 반환
 */
public final class HlsSignedUrlUtil { // Nginx secure_link 서명 유틸리티
    private HlsSignedUrlUtil() {} // 인스턴스화 방지

    /**
     * secure_link 서명값(st) 생성
     * - 포맷: MD5(expires + uriPath + " " + secret) → Base64
     */
    public static String generateSignature(String uriPath, long expiresEpochSeconds, String secret) {
        String data = expiresEpochSeconds + uriPath + " " + secret; // Nginx 측과 동일한 연결 문자열
        byte[] md5;
        try {
            md5 = MessageDigest.getInstance("MD5").digest(data.getBytes(StandardCharsets.UTF_8)); // MD5 해시
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e); // 환경 문제 시 런타임 예외
        }
        return Base64.getEncoder().encodeToString(md5); // Base64 인코딩(패딩 포함)
    }

    /**
     * 현재 시각 기준 TTL초 뒤 만료 시각(epoch seconds) 계산
     */
    public static long defaultExpiryFromNowSeconds(long ttlSeconds) {
        return Instant.now().getEpochSecond() + Math.max(1, ttlSeconds); // 최소 1초 보장
    }
}


