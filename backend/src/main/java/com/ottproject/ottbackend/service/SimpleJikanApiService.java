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
import com.ottproject.ottbackend.dto.jikan.AnimeStaffJikanDto;

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
    private double maxRequestsPerSecond; // 초당 허용 요청수

    @Value("${jikan.rate-limit.backoff-ms:1200}")
    private long defaultBackoffMs; // 기본 백오프(ms)

    @Value("${jikan.rate-limit.retry.backoff-ms:5000}")
    private long retryBackoffMs; // 429 시 대기(ms)
    
    // Circuit Breaker 패턴을 위한 상태 관리 (스레드 안전)
    private volatile boolean circuitOpen = false;
    private volatile long lastFailureTime = 0;
    private final java.util.concurrent.atomic.AtomicInteger consecutiveFailures = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 5; // 연속 실패 임계값 (더 관대하게)
    private static final long CIRCUIT_TIMEOUT = 300000; // 5분 후 재시도 (더 긴 대기)
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
     * 애니메이션 스태프 정보 조회 (/anime/{id}/staff)
     */
    public List<AnimeStaffJikanDto.StaffItem> getAnimeStaff(Long malId) {
        if (isCircuitOpen()) {
            log.warn("🚫 Circuit Breaker 열림: staff 호출 차단 (MAL ID: {})", malId);
            return java.util.Collections.emptyList();
        }

        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                String url = baseUrl + "/anime/" + malId + "/staff";
                log.info("Jikan API 호출(staff): {} (시도: {}/{})", url.replaceAll("\\d+", "***"), retryCount + 1, maxRetries);

                ResponseEntity<AnimeStaffJikanDto> response = restTemplate.getForEntity(url, AnimeStaffJikanDto.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    AnimeStaffJikanDto dto = response.getBody();
                    List<AnimeStaffJikanDto.StaffItem> data = dto == null ? java.util.List.of() : dto.getData();
                    log.info("✅ Staff API 성공: MAL ID {}, 데이터 크기: {}", malId, data != null ? data.size() : "null");
                    
                    // 디버깅: 실제 데이터 내용 확인
                    if (data != null && !data.isEmpty()) {
                        log.info("🔍 Staff 데이터 샘플 (처음 3개):");
                        for (int i = 0; i < Math.min(3, data.size()); i++) {
                            var item = data.get(i);
                            log.info("  [{}] 이름: {}, 포지션: {}, Person 객체: {}", 
                                i, item.getName(), item.getPositions(), item.getPerson());
                        }
                        
                        // Director 포지션 찾기
                        log.info("🎬 Director 검색 결과:");
                        for (var item : data) {
                            if (item.getPositions() != null && item.getPositions().contains("Director")) {
                                log.info("  ✅ 감독 발견: {} (포지션: {})", item.getName(), item.getPositions());
                            }
                        }
                    }
                    
                    recordSuccess();
                    return data == null ? java.util.List.of() : data;
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("Rate limit 도달(staff): MAL ID {} (시도: {}/{})", malId, retryCount + 1, maxRetries);
                    handleRateLimitRetry();
                    retryCount++;
                    continue;
                }

                log.warn("스태프 조회 실패: MAL ID {} (상태: {})", malId, response.getStatusCode());
                return java.util.List.of();

            } catch (Exception e) {
                log.error("스태프 조회 중 오류: MAL ID {} (시도: {}/{})", malId, retryCount + 1, maxRetries, e);
                retryCount++;
                if (retryCount < maxRetries) {
                    handleRateLimitRetry();
                }
            }
        }

        recordFailure();
        return java.util.List.of();
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
                        log.info("더 이상 데이터가 없음. 페이지: {} (총 수집: {}개)", page, allAnimeIds.size());
                        break;
                    }
                    
                    log.info("페이지 {} 응답: {}개 항목 수신", page, dataList.size());
                    
                    for (TopAnimePageJikanDto.AnimeSummary anime : dataList) {
                        if (allAnimeIds.size() >= limit) {
                            log.info("목표 개수 도달: {}개 (요청: {}개)", allAnimeIds.size(), limit);
                            break;
                        }
                        
                        Long malId = anime.getMal_id();
                        allAnimeIds.add(malId);
                    }
                    
                    log.info("페이지 {} 완료: {}개 수집 (총 {}개, 목표: {}개)", page, dataList.size(), allAnimeIds.size(), limit);
                    
                    // Rate limit 대응 + 페이지 간 소폭 지연(안정화)
                    delayForRateLimit();
                    try { Thread.sleep(Math.max(250L, defaultBackoffMs)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    page++;
                    
                } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("페이지 {} 조회 시 Rate limit 도달: 백오프 후 재시도 (현재 수집: {}개)", page, allAnimeIds.size());
                    handleRateLimitRetry();
                    // page 유지 후 재시도
                } else {
                    log.warn("페이지 {} 조회 실패 (상태: {}) - 수집 중단 (현재 수집: {}개)", page, response.getStatusCode(), allAnimeIds.size());
                    break;
                }
            }
            
            log.info("인기 애니메이션 ID 조회 완료: 총 {}개 (요청: {}개)", allAnimeIds.size(), limit);
            return allAnimeIds;
            
        } catch (Exception e) {
            log.error("인기 애니메이션 조회 중 오류 발생 (현재 수집: {}개, 목표: {}개)", allAnimeIds.size(), limit, e);
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
        long minDelayMs = Math.max(defaultBackoffMs, (long)Math.ceil(1000.0 / Math.max(1.0, maxRequestsPerSecond)));
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
        long delayMs = Math.max(5000L, retryBackoffMs); // 최소 5초 대기
        try {
            log.warn("Rate limit 도달로 인한 대기: {}ms ({}초)", delayMs, delayMs / 1000);
            Thread.sleep(delayMs);
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
