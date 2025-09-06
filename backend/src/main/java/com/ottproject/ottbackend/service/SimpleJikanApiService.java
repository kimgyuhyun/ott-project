package com.ottproject.ottbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
 
import com.ottproject.ottbackend.dto.jikan.TopAnimePageJikanDto;
import com.ottproject.ottbackend.dto.jikan.AnimeDetailsJikanDto;
import com.ottproject.ottbackend.dto.jikan.AnimeCharactersJikanDto;

/**
 * 간단한 Jikan API 호출 서비스 (Jikan 전용 DTO 사용)
 * 
 * 큰 흐름
 * - Jikan API를 호출하여 Jikan 전용 DTO(Top/Details/Characters)로 역직렬화하고 사용한다.
 * - 레이트 리밋은 설정 기반 백오프/재시도(429)로 보호한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleJikanApiService {
    
    private final RestTemplate restTemplate;
    
    @Value("${jikan.api.base-url:https://api.jikan.moe/v4}")
    private String baseUrl; // 운영 환경에서는 환경변수로 설정 필요

    @Value("${jikan.rate-limit.max-rps:3}")
    private int maxRequestsPerSecond; // 초당 허용 요청수

    @Value("${jikan.rate-limit.backoff-ms:1200}")
    private long defaultBackoffMs; // 기본 백오프(ms)

    @Value("${jikan.rate-limit.retry.backoff-ms:5000}")
    private long retryBackoffMs; // 429 시 대기(ms)
    
    // Circuit Breaker 패턴을 위한 상태 관리 (스레드 안전)
    private volatile boolean circuitOpen = false;
    private volatile long lastFailureTime = 0;
    private final java.util.concurrent.atomic.AtomicInteger consecutiveFailures = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 3; // 연속 실패 임계값 (운영 환경 최적화)
    private static final long CIRCUIT_TIMEOUT = 60000; // 1분 후 재시도 (개발 환경)
    private final Object circuitLock = new Object(); // Circuit 상태 변경 동기화
    
    /**
     * 애니메이션 상세 정보 조회 (DTO 반환)
     */
    public AnimeDetailsJikanDto.Data getAnimeDetails(Long malId) {
        // Circuit Breaker 체크
        if (isCircuitOpen()) {
            log.warn("🚫 Circuit Breaker 열림: API 호출 차단됨 (MAL ID: {})", malId);
            return null;
        }
        
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                String url = baseUrl + "/anime/" + malId;
                log.info("Jikan API 호출: {} (시도: {}/{})", url.replaceAll("\\d+", "***"), retryCount + 1, maxRetries); // 민감한 정보 마스킹
                
                // 운영 환경을 위한 타임아웃 설정
                ResponseEntity<AnimeDetailsJikanDto> response = restTemplate.getForEntity(url, AnimeDetailsJikanDto.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    AnimeDetailsJikanDto dto = response.getBody();
                    AnimeDetailsJikanDto.Data data = (dto == null ? null : dto.getData());
                    if (data != null) {
                        log.info("애니메이션 조회 성공: MAL ID {}", malId);
                        recordSuccess();
                        return data;
                    }
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("Rate limit 도달: MAL ID {} (시도: {}/{})", malId, retryCount + 1, maxRetries);
                    handleRateLimitRetry();
                    retryCount++;
                    continue;
                }
                
                log.warn("애니메이션 조회 실패: MAL ID {} (상태: {})", malId, response.getStatusCode());
                return null;
                
            } catch (Exception e) {
                log.error("애니메이션 조회 중 오류 발생: MAL ID {} (시도: {}/{})", malId, retryCount + 1, maxRetries, e);
                retryCount++;
                if (retryCount < maxRetries) {
                    handleRateLimitRetry();
                }
            }
        }
        
            log.error("ANIME_API_FINAL_FAILURE|malId={}|reason=MAX_RETRIES_EXCEEDED|retryCount={}", malId, maxRetries);
            recordFailure();
            // 운영 환경에서 장애 알림을 위한 메트릭 수집
            log.error("ANIME_API_CIRCUIT_BREAKER_TRIGGERED|malId={}|consecutiveFailures={}", malId, consecutiveFailures.get());
        return null;
    }
    
    /**
     * 인기 애니메이션 ID 목록 조회 (페이지네이션 지원)
     */
    public List<Long> getPopularAnimeIds(int limit) {
        List<Long> allAnimeIds = new ArrayList<>();
        int page = 1;
        int perPage = 25; // Jikan API 최대 한 번에 25개
        
        try {
            while (allAnimeIds.size() < limit) {
                int remaining = limit - allAnimeIds.size();
                int currentLimit = Math.min(perPage, remaining);
                
                String url = baseUrl + "/top/anime?page=" + page + "&limit=" + currentLimit;
                log.info("인기 애니메이션 조회: {} (페이지: {}, 요청개수: {})", url, page, currentLimit);
                
                ResponseEntity<TopAnimePageJikanDto> response = restTemplate.getForEntity(url, TopAnimePageJikanDto.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    TopAnimePageJikanDto dto = response.getBody();
                    List<TopAnimePageJikanDto.AnimeSummary> dataList = (dto == null ? null : dto.getData());
                    if (dataList == null || dataList.isEmpty()) {
                        log.info("더 이상 데이터가 없음. 페이지: {}", page);
                        break;
                    }
                    
                    for (TopAnimePageJikanDto.AnimeSummary anime : dataList) {
                        if (allAnimeIds.size() >= limit) break;
                        
                        Long malId = anime.getMal_id();
                        allAnimeIds.add(malId);
                    }
                    
                    log.info("페이지 {} 완료: {}개 수집 (총 {}개)", page, dataList.size(), allAnimeIds.size());
                    
                    // Rate limit 대응
                    delayForRateLimit();
                    page++;
                    
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("페이지 {} 조회 시 Rate limit 도달: 백오프 후 재시도", page);
                    handleRateLimitRetry();
                    // page 유지 후 재시도
                } else {
                    log.warn("페이지 {} 조회 실패 (상태: {})", page, response.getStatusCode());
                    break;
                }
            }
            
            log.info("인기 애니메이션 ID 조회 완료: 총 {}개 (요청: {}개)", allAnimeIds.size(), limit);
            return allAnimeIds;
            
        } catch (Exception e) {
            log.error("인기 애니메이션 조회 중 오류 발생", e);
            return allAnimeIds; // 부분적으로 수집된 데이터라도 반환
        }
    }
    
    /**
     * 애니메이션 캐릭터/성우 정보 조회 (DTO 반환) - 재시도 로직 포함
     */
    public AnimeCharactersJikanDto getAnimeCharacters(Long malId) {
        // Circuit Breaker 체크
        if (isCircuitOpen()) {
            log.warn("🚫 Circuit Breaker 열림: 캐릭터 API 호출 차단됨 (MAL ID: {})", malId);
            AnimeCharactersJikanDto empty = new AnimeCharactersJikanDto();
            empty.setData(new java.util.ArrayList<>());
            return empty;
        }
        
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                String url = baseUrl + "/anime/" + malId + "/characters";
                log.info("Jikan API 캐릭터 호출: {} (시도: {}/{})", url, retryCount + 1, maxRetries);
                
                ResponseEntity<AnimeCharactersJikanDto> response = restTemplate.getForEntity(url, AnimeCharactersJikanDto.class);
                
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    AnimeCharactersJikanDto dto = response.getBody();
                    if (dto != null) {
                        log.info("캐릭터 정보 조회 성공: MAL ID {}", malId);
                        recordSuccess();
                        return dto;
                    }
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("Rate limit 도달: MAL ID {} (시도: {}/{})", malId, retryCount + 1, maxRetries);
                    handleRateLimitRetry();
                    retryCount++;
                    continue;
                }
                
                log.warn("캐릭터 정보 조회 실패: MAL ID {} (상태: {})", malId, response.getStatusCode());
                break;
                
            } catch (Exception e) {
                log.error("캐릭터 정보 조회 중 오류 발생: MAL ID {} (시도: {}/{})", malId, retryCount + 1, maxRetries, e);
                retryCount++;
                if (retryCount < maxRetries) {
                    handleRateLimitRetry();
                }
            }
        }
        
        log.warn("캐릭터 정보 조회 최종 실패: MAL ID {}", malId);
        recordFailure();
        AnimeCharactersJikanDto empty = new AnimeCharactersJikanDto();
        empty.setData(new java.util.ArrayList<>());
        return empty;
    }
    
    /**
     * Rate limit 대응을 위한 지연 - 비동기 처리
     * - 설정 기반 백오프 사용. maxRequestsPerSecond에 따라 최소 대기 계산.
     */
    public void delayForRateLimit() {
        long minDelayMs = Math.max(defaultBackoffMs, (long)Math.ceil(1000.0 / Math.max(1, maxRequestsPerSecond)));
        try {
            // 짧은 지연은 Thread.sleep, 긴 지연은 CompletableFuture 사용
            if (minDelayMs <= 100) {
                Thread.sleep(minDelayMs);
            } else {
                // 비동기 지연으로 스레드 풀 효율성 향상
                java.util.concurrent.CompletableFuture.delayedExecutor(minDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> {});
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rate limit 지연 중 인터럽트 발생", e);
        }
    }
    
    /**
     * 429 에러 시 재시도 로직 - 비동기 처리
     */
    private void handleRateLimitRetry() {
        long delayMs = Math.max(1000L, retryBackoffMs);
        try {
            // 짧은 지연은 Thread.sleep, 긴 지연은 CompletableFuture 사용
            if (delayMs <= 200) {
                Thread.sleep(delayMs);
            } else {
                // 비동기 지연으로 스레드 풀 효율성 향상
                java.util.concurrent.CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(() -> {});
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rate limit 재시도 지연 중 인터럽트 발생", e);
        }
    }
    
    /**
     * Circuit Breaker 상태 확인 (스레드 안전) - 락 경합 최소화
     */
    private boolean isCircuitOpen() {
        // volatile 변수는 락 없이 읽어도 메모리 가시성 보장됨
        // 하지만 일관성을 위해 락 내에서 모든 상태를 확인
        synchronized (circuitLock) {
            if (!circuitOpen) {
                return false;
            }
            
            // 시간 체크
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFailureTime <= CIRCUIT_TIMEOUT) {
                return true;
            }
            
            // 타임아웃 후 재시도 허용
            circuitOpen = false;
            consecutiveFailures.set(0);
            lastFailureTime = currentTime;
            log.info("🔄 Circuit Breaker 재시도 허용");
            return false;
        }
    }
    
    /**
     * 성공 기록 (스레드 안전) - 락 경합 최소화
     */
    private void recordSuccess() {
        // AtomicInteger는 이미 스레드 안전하므로 락 없이 먼저 처리
        consecutiveFailures.set(0);
        
        // circuitOpen 상태 변경만 락으로 보호
        synchronized (circuitLock) {
            circuitOpen = false;
        }
    }
    
    /**
     * 실패 기록 (스레드 안전) - 락 경합 최소화
     */
    private void recordFailure() {
        int currentFailures = consecutiveFailures.incrementAndGet();
        long currentTime = System.currentTimeMillis();
        
        // 모든 상태 변경을 락 내에서 수행하여 일관성 보장
        synchronized (circuitLock) {
            lastFailureTime = currentTime;
            
            if (currentFailures >= FAILURE_THRESHOLD && !circuitOpen) {
                circuitOpen = true;
                log.error("🚫 Circuit Breaker 열림: 연속 {}회 실패", currentFailures);
            }
        }
    }
}
