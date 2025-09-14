package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.AnimeListDto;
import com.ottproject.ottbackend.mybatis.AnimeQueryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 개인화 추천 서비스
 * 
 * 큰 흐름
 * - 사용자 찜/시청진도/평점 기반으로 태그 가중치 계산
 * - Redis에 태그 선호도 캐싱
 * - 가중치 기반으로 개인화 추천 목록 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalizedRecommendationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AnimeQueryMapper animeQueryMapper;

    // Redis 키 패턴
    private static final String USER_FAVORITE_TAGS = "u:%d:favorite_tags";
    private static final String USER_WATCHED_ANIME = "u:%d:watched";
    private static final String USER_LIKED_TAGS = "u:%d:liked_tags";
    private static final String USER_RECOMMENDATIONS = "u:%d:recommendations";
    private static final String TREND_24H = "trend:24h";

    /**
     * 사용자 개인화 추천 목록 조회
     */
    public List<AnimeListDto> getPersonalizedRecommendations(Long userId, int size) {
        try {
            // 1. Redis에서 캐시된 추천 확인
            String cacheKey = String.format(USER_RECOMMENDATIONS, userId);
            @SuppressWarnings("unchecked")
            List<AnimeListDto> cached = (List<AnimeListDto>) redisTemplate.opsForValue().get(cacheKey);
            
            if (cached != null && !cached.isEmpty()) {
                log.debug("Redis 캐시에서 추천 목록 반환: userId={}, size={}", userId, cached.size());
                return cached.stream().limit(size).toList();
            }

            // 2. 개인화 추천 생성
            List<AnimeListDto> recommendations = generatePersonalizedRecommendations(userId, size);
            
            // 3. Redis에 캐시 (30분)
            redisTemplate.opsForValue().set(cacheKey, recommendations, 30, TimeUnit.MINUTES);
            
            log.info("개인화 추천 생성 완료: userId={}, size={}", userId, recommendations.size());
            return recommendations;
            
        } catch (Exception e) {
            log.error("개인화 추천 생성 실패: userId={}", userId, e);
            // 실패 시 기본 추천 (인기작)
            return getFallbackRecommendations(size);
        }
    }

    /**
     * 개인화 추천 생성 로직
     */
    private List<AnimeListDto> generatePersonalizedRecommendations(Long userId, int size) {
        // 1. 사용자 태그 선호도 계산
        Map<Long, Double> tagWeights = calculateUserTagWeights(userId);
        
        if (tagWeights.isEmpty()) {
            log.debug("사용자 태그 선호도 없음, 기본 추천 반환: userId={}", userId);
            return getFallbackRecommendations(size);
        }

        // 2. 상위 태그 3개 선택
        List<Long> topTags = tagWeights.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        // 3. 시청한 작품 제외하고 추천
        Set<Long> watchedAnime = getWatchedAnimeIds(userId);
        
        // 4. 태그 기반 애니메이션 조회 (후보를 적절히 제한)
        List<AnimeListDto> candidates = animeQueryMapper.findAniList(
                null, // status
                null, // genreIds
                null, // genreCount
                topTags, // tagIds
                null, // minRating
                null, // year
                null, // quarter
                null, // type
                null, // isDub
                null, // isSubtitle
                null, // isExclusive
                null, // isCompleted
                null, // isNew
                null, // isPopular
                "rating", // sort
                Math.min(size * 2, 20), // limit: 최대 20개로 제한
                0, // offset
                null, // cursorId
                null, // cursorRating
                null // cursorIsPopular
        );

        // 5. 시청한 작품 제외 및 태그 가중치로 정렬
        return candidates.stream()
                .filter(anime -> !watchedAnime.contains(anime.getAniId()))
                .sorted((a, b) -> {
                    double scoreA = calculateAnimeScore(a, tagWeights);
                    double scoreB = calculateAnimeScore(b, tagWeights);
                    return Double.compare(scoreB, scoreA);
                })
                .limit(size)
                .toList();
    }

    /**
     * 사용자 태그 가중치 계산
     */
    private Map<Long, Double> calculateUserTagWeights(Long userId) {
        Map<Long, Double> tagWeights = new HashMap<>();
        
        try {
            // 1. 찜한 작품의 태그 가중치 (가중치: 3.0)
            addTagWeightsFromFavorites(userId, tagWeights, 3.0);
            
            // 2. 시청한 작품의 태그 가중치 (가중치: 2.0)
            addTagWeightsFromWatched(userId, tagWeights, 2.0);
            
            // 3. 높은 평점 준 작품의 태그 가중치 (가중치: 4.0)
            addTagWeightsFromHighRatings(userId, tagWeights, 4.0);
            
            // 4. Redis에 태그 가중치 캐시
            String redisKey = String.format(USER_FAVORITE_TAGS, userId);
            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
            
            // 기존 데이터 클리어
            redisTemplate.delete(redisKey);
            
            // 새 데이터 추가
            for (Map.Entry<Long, Double> entry : tagWeights.entrySet()) {
                zSetOps.add(redisKey, entry.getKey().toString(), entry.getValue());
            }
            
            // TTL 설정 (1시간)
            redisTemplate.expire(redisKey, 1, TimeUnit.HOURS);
            
        } catch (Exception e) {
            log.error("태그 가중치 계산 실패: userId={}", userId, e);
        }
        
        return tagWeights;
    }

    /**
     * 찜한 작품의 태그 가중치 추가
     */
    private void addTagWeightsFromFavorites(Long userId, Map<Long, Double> tagWeights, double weight) {
        try {
            // 찜한 작품의 태그 조회 (간단한 구현)
            List<Long> favoriteAnimeIds = animeQueryMapper.findFavoriteAnimeIds(userId);
            
            for (Long animeId : favoriteAnimeIds) {
                List<Long> tagIds = animeQueryMapper.findTagIdsByAnimeId(animeId);
                for (Long tagId : tagIds) {
                    tagWeights.merge(tagId, weight, Double::sum);
                }
            }
        } catch (Exception e) {
            log.warn("찜한 작품 태그 가중치 추가 실패: userId={}", userId, e);
        }
    }

    /**
     * 시청한 작품의 태그 가중치 추가
     */
    private void addTagWeightsFromWatched(Long userId, Map<Long, Double> tagWeights, double weight) {
        try {
            Set<Long> watchedAnimeIds = getWatchedAnimeIds(userId);
            
            for (Long animeId : watchedAnimeIds) {
                List<Long> tagIds = animeQueryMapper.findTagIdsByAnimeId(animeId);
                for (Long tagId : tagIds) {
                    tagWeights.merge(tagId, weight, Double::sum);
                }
            }
        } catch (Exception e) {
            log.warn("시청한 작품 태그 가중치 추가 실패: userId={}", userId, e);
        }
    }

    /**
     * 높은 평점 작품의 태그 가중치 추가
     */
    private void addTagWeightsFromHighRatings(Long userId, Map<Long, Double> tagWeights, double weight) {
        try {
            // 4.0 이상 평점 준 작품들
            List<Long> highRatedAnimeIds = animeQueryMapper.findHighRatedAnimeIds(userId, 4.0);
            
            for (Long animeId : highRatedAnimeIds) {
                List<Long> tagIds = animeQueryMapper.findTagIdsByAnimeId(animeId);
                for (Long tagId : tagIds) {
                    tagWeights.merge(tagId, weight, Double::sum);
                }
            }
        } catch (Exception e) {
            log.warn("높은 평점 작품 태그 가중치 추가 실패: userId={}", userId, e);
        }
    }

    /**
     * 시청한 작품 ID 조회
     */
    private Set<Long> getWatchedAnimeIds(Long userId) {
        try {
            String redisKey = String.format(USER_WATCHED_ANIME, userId);
            @SuppressWarnings("unchecked")
            Set<Object> watched = redisTemplate.opsForSet().members(redisKey);
            
            if (watched != null && !watched.isEmpty()) {
                return watched.stream()
                        .map(obj -> Long.valueOf(obj.toString()))
                        .collect(java.util.stream.Collectors.toSet());
            }
            
            // Redis에 없으면 DB에서 조회
            List<Long> watchedAnimeList = animeQueryMapper.findWatchedAnimeIds(userId);
            Set<Long> watchedAnimeIds = new HashSet<>(watchedAnimeList);
            
            // Redis에 캐시
            if (!watchedAnimeIds.isEmpty()) {
                Object[] animeIdObjects = watchedAnimeIds.stream()
                        .map(String::valueOf)
                        .toArray();
                redisTemplate.opsForSet().add(redisKey, animeIdObjects);
                redisTemplate.expire(redisKey, 1, TimeUnit.HOURS);
            }
            
            return watchedAnimeIds;
            
        } catch (Exception e) {
            log.warn("시청한 작품 ID 조회 실패: userId={}", userId, e);
            return Collections.emptySet();
        }
    }

    /**
     * 애니메이션 점수 계산 (태그 가중치 기반)
     */
    private double calculateAnimeScore(AnimeListDto anime, Map<Long, Double> tagWeights) {
        try {
            List<Long> animeTagIds = animeQueryMapper.findTagIdsByAnimeId(anime.getAniId());
            
            double score = 0.0;
            for (Long tagId : animeTagIds) {
                score += tagWeights.getOrDefault(tagId, 0.0);
            }
            
            // 평점도 고려 (0.1 가중치)
            score += anime.getRating() * 0.1;
            
            return score;
        } catch (Exception e) {
            log.warn("애니메이션 점수 계산 실패: animeId={}", anime.getAniId(), e);
            return anime.getRating(); // 기본값: 평점
        }
    }

    /**
     * 기본 추천 (인기작)
     */
    private List<AnimeListDto> getFallbackRecommendations(int size) {
        try {
            return animeQueryMapper.findAniList(
                    null, // status
                    null, // genreIds
                    null, // genreCount
                    null, // tagIds
                    null, // minRating
                    null, // year
                    null, // quarter
                    null, // type
                    null, // isDub
                    null, // isSubtitle
                    null, // isExclusive
                    null, // isCompleted
                    null, // isNew
                    true, // isPopular
                    "rating", // sort
                    size, // limit
                    0, // offset
                    null, // cursorId
                    null, // cursorRating
                    null // cursorIsPopular
            );
        } catch (Exception e) {
            log.error("기본 추천 조회 실패", e);
            return Collections.emptyList();
        }
    }

    /**
     * 사용자 활동 기록 (시청/찜/평점)
     */
    public void recordUserActivity(Long userId, Long animeId, String activityType) {
        try {
            switch (activityType) {
                case "view" -> {
                    // 시청 기록
                    String watchedKey = String.format(USER_WATCHED_ANIME, userId);
                    redisTemplate.opsForSet().add(watchedKey, animeId.toString());
                    redisTemplate.expire(watchedKey, 1, TimeUnit.HOURS);
                    
                    // 추천 캐시 무효화
                    String recKey = String.format(USER_RECOMMENDATIONS, userId);
                    redisTemplate.delete(recKey);
                }
                case "favorite" -> {
                    // 찜 기록
                    String favKey = String.format(USER_FAVORITE_TAGS, userId);
                    redisTemplate.delete(favKey); // 태그 가중치 재계산 필요
                    
                    // 추천 캐시 무효화
                    String recKey = String.format(USER_RECOMMENDATIONS, userId);
                    redisTemplate.delete(recKey);
                }
                case "rating" -> {
                    // 평점 기록
                    String likedKey = String.format(USER_LIKED_TAGS, userId);
                    redisTemplate.delete(likedKey); // 태그 가중치 재계산 필요
                    
                    // 추천 캐시 무효화
                    String recKey = String.format(USER_RECOMMENDATIONS, userId);
                    redisTemplate.delete(recKey);
                }
            }
            
            // 트렌드 카운팅
            redisTemplate.opsForZSet().incrementScore(TREND_24H, animeId.toString(), 1);
            redisTemplate.expire(TREND_24H, 1, TimeUnit.DAYS);
            
        } catch (Exception e) {
            log.error("사용자 활동 기록 실패: userId={}, animeId={}, activity={}", userId, animeId, activityType, e);
        }
    }
}
