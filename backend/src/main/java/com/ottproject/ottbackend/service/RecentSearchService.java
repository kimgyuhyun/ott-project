package com.ottproject.ottbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

/**
 * RecentSearchService
 *
 * 큰 흐름
 * - 최근 검색어 Redis 저장/조회 서비스 (MyBatis 패턴 유지)
 *
 * 메서드 개요
 * - list: 최근 검색어 목록 조회
 * - add: 검색어 추가 (중복 제거, LRU 유지)
 * - remove: 특정 검색어 제거
 * - clear: 전체 검색어 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentSearchService {
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String KEY_PREFIX = "ott:recent-search:v1:";
    private static final String ENTRY_DELIM = "\t"; // "원문\t정규화값"
    private static final int MAX_SIZE = 10;
    private static final int TTL_DAYS = 30;
    
    // 금지어 패턴 (예시)
    private static final Set<String> FORBIDDEN_TERMS = Set.of(
        "admin", "root", "password", "test", "null", "undefined"
    );
    
    // Lua 스크립트: 검색어 추가 (중복 제거, LRU 유지)
    private static final String LUA_ADD = """
        local key = KEYS[1]
        local original = ARGV[1]
        local normalized = ARGV[2]
        local maxLen = tonumber(ARGV[3])
        local ttlSec = tonumber(ARGV[4])
        local entry = original .. '\t' .. normalized
        
        -- 기존 중복 항목 제거
        local all = redis.call('LRANGE', key, 0, -1)
        for i, v in ipairs(all) do
            local norm = string.match(v, '^.-\t(.*)$')
            if norm == normalized then
                redis.call('LREM', key, 0, v)
            end
        end
        
        -- 새 항목 추가
        redis.call('LPUSH', key, entry)
        redis.call('LTRIM', key, 0, maxLen - 1)
        redis.call('EXPIRE', key, ttlSec)
        
        return redis.call('LRANGE', key, 0, -1)
        """;
    
    // Lua 스크립트: 검색어 제거
    private static final String LUA_REMOVE = """
        local key = KEYS[1]
        local original = ARGV[1]
        local normalized = ARGV[2]
        local ttlSec = tonumber(ARGV[3])
        
        local all = redis.call('LRANGE', key, 0, -1)
        for i, v in ipairs(all) do
            local norm = string.match(v, '^.-\t(.*)$')
            if norm == normalized then
                redis.call('LREM', key, 0, v)
            end
        end
        
        redis.call('EXPIRE', key, ttlSec)
        return redis.call('LRANGE', key, 0, -1)
        """;
    
    /**
     * 최근 검색어 목록 조회
     */
    public List<String> list(String subjectId) {
        String key = buildKey(subjectId);
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("[Search][Recent] LIST start key={}", key);
            
            List<String> rawEntries = redisTemplate.opsForList().range(key, 0, -1);
            if (rawEntries == null || rawEntries.isEmpty()) {
                log.info("[Search][Recent] LIST end ms={} len=0", System.currentTimeMillis() - startTime);
                return Collections.emptyList();
            }
            
            // 원문만 추출
            List<String> results = new ArrayList<>();
            for (String entry : rawEntries) {
                int delimIndex = entry.indexOf(ENTRY_DELIM);
                String original = delimIndex > -1 ? entry.substring(0, delimIndex) : entry;
                results.add(original);
            }
            
            // 슬라이딩 TTL 갱신
            redisTemplate.expire(key, Duration.ofDays(TTL_DAYS));
            
            log.info("[Search][Recent] LIST end ms={} len={}", System.currentTimeMillis() - startTime, results.size());
            return results;
            
        } catch (Exception e) {
            log.error("[Search][Recent] LIST failed key={} error={}", key, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 검색어 추가 (중복 제거, LRU 유지)
     */
    @Transactional
    public List<String> add(String subjectId, String termOriginal) {
        String key = buildKey(subjectId);
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("[Search][Recent] ADD start key={} term={}", key, termOriginal);
            
            // 입력 검증
            String sanitized = sanitizeInput(termOriginal);
            if (sanitized.isEmpty()) {
                log.warn("[Search][Recent] ADD invalid input key={} term={}", key, termOriginal);
                return list(subjectId);
            }
            
            String normalized = normalizeForCompare(sanitized);
            
            // 금지어 검사
            if (isForbidden(normalized)) {
                log.warn("[Search][Recent] ADD forbidden term key={} term={}", key, termOriginal);
                return list(subjectId);
            }
            
            // Lua 스크립트 실행
            DefaultRedisScript<List<String>> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_ADD);
            script.setResultType((Class<List<String>>) (Class<?>) List.class);
            List<String> rawResults = redisTemplate.execute(script, 
                Collections.singletonList(key), 
                sanitized, normalized, String.valueOf(MAX_SIZE), String.valueOf(TTL_DAYS * 24 * 60 * 60)
            );
            
            // 결과 파싱
            List<String> results = new ArrayList<>();
            if (rawResults != null) {
                for (String entry : rawResults) {
                    int delimIndex = entry.indexOf(ENTRY_DELIM);
                    String original = delimIndex > -1 ? entry.substring(0, delimIndex) : entry;
                    results.add(original);
                }
            }
            
            log.info("[Search][Recent] ADD end ms={} len={}", System.currentTimeMillis() - startTime, results.size());
            return results;
            
        } catch (Exception e) {
            log.error("[Search][Recent] ADD failed key={} term={} error={}", key, termOriginal, e.getMessage(), e);
            return list(subjectId);
        }
    }
    
    /**
     * 특정 검색어 제거
     */
    @Transactional
    public List<String> remove(String subjectId, String termOriginal) {
        String key = buildKey(subjectId);
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("[Search][Recent] REMOVE start key={} term={}", key, termOriginal);
            
            String sanitized = sanitizeInput(termOriginal);
            String normalized = normalizeForCompare(sanitized);
            
            // Lua 스크립트 실행
            DefaultRedisScript<List<String>> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_REMOVE);
            script.setResultType((Class<List<String>>) (Class<?>) List.class);
            List<String> rawResults = redisTemplate.execute(script, 
                Collections.singletonList(key), 
                sanitized, normalized, String.valueOf(TTL_DAYS * 24 * 60 * 60)
            );
            
            // 결과 파싱
            List<String> results = new ArrayList<>();
            if (rawResults != null) {
                for (String entry : rawResults) {
                    int delimIndex = entry.indexOf(ENTRY_DELIM);
                    String original = delimIndex > -1 ? entry.substring(0, delimIndex) : entry;
                    results.add(original);
                }
            }
            
            log.info("[Search][Recent] REMOVE end ms={} len={}", System.currentTimeMillis() - startTime, results.size());
            return results;
            
        } catch (Exception e) {
            log.error("[Search][Recent] REMOVE failed key={} term={} error={}", key, termOriginal, e.getMessage(), e);
            return list(subjectId);
        }
    }
    
    /**
     * 전체 검색어 삭제
     */
    @Transactional
    public void clear(String subjectId) {
        String key = buildKey(subjectId);
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("[Search][Recent] CLEAR start key={}", key);
            
            redisTemplate.delete(key);
            
            log.info("[Search][Recent] CLEAR end ms={}", System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            log.error("[Search][Recent] CLEAR failed key={} error={}", key, e.getMessage(), e);
        }
    }
    
    /**
     * Redis 키 생성
     */
    private String buildKey(String subjectId) {
        return KEY_PREFIX + subjectId;
    }
    
    /**
     * 입력값 정리 (공백 처리, 길이 제한)
     */
    private String sanitizeInput(String input) {
        if (input == null) return "";
        
        // 공백 정리
        String sanitized = input.trim().replaceAll("\\s+", " ");
        
        // 길이 제한
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80);
        }
        
        return sanitized;
    }
    
    /**
     * 중복 비교용 정규화 (NFKC, 소문자, 공백 압축)
     */
    private String normalizeForCompare(String input) {
        if (input == null) return "";
        
        // NFKC 정규화 (한글/특수문자 변형 방지)
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC);
        
        // 소문자 변환
        normalized = normalized.toLowerCase();
        
        // 공백 압축
        normalized = normalized.replaceAll("\\s+", " ");
        
        return normalized.trim();
    }
    
    /**
     * 금지어 검사
     */
    private boolean isForbidden(String normalized) {
        return FORBIDDEN_TERMS.contains(normalized.toLowerCase());
    }
}
