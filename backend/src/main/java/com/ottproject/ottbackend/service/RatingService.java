package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Rating;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.mybatis.RatingQueryMapper;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.RatingRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class RatingService {
    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepository; // JPA CUD
    private final RatingQueryMapper ratingQueryMapper; // MyBatis 조회
    private final UserRepository userRepository;
    private final AnimeRepository animeRepository;

    public void createOrUpdateRating(Long userId, Long aniId, Double score) {
        User user = userRepository.findById(userId).orElseThrow();
        Anime anime = animeRepository.findById(aniId).orElseThrow();

        Rating rating = ratingRepository.findByUserIdAndAnimeId(userId, aniId)
                .orElseGet(() -> Rating.createRating(user, anime, 0.0));
        rating.setScore(score);
        ratingRepository.save(rating);

        // 동기화: 애니의 집계 평점/카운트 갱신
        updateAnimeAggregates(aniId);
    }

    @Transactional(readOnly = true)
    public Double getUserRating(Long userId, Long aniId) {
        Double v = ratingQueryMapper.findUserRatingByAnimeId(userId, aniId);
        return v == null ? 0.0 : v;
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> getDistribution(Long aniId) {
        Map<String, Integer> result = new HashMap<>();
        double v = 1.0;
        while (v <= 5.0 + 1e-9) {
            result.put(String.format("%.1f", v), 0);
            v += 0.5;
        }
        try {
            List<java.util.Map<String, Object>> rows = ratingQueryMapper.findRatingDistributionByAnimeId(aniId);
            log.debug("getDistribution raw rows aniId={} -> {}", aniId, rows);
            if (rows != null) {
                for (java.util.Map<String, Object> row : rows) {
                    if (row == null) continue;
                    Object ratingObj = row.get("rating");
                    Object countObj = row.get("count");
                    String bucketKey = null;
                    Integer countVal = null;

                    if (ratingObj instanceof Number) {
                        double d = ((Number) ratingObj).doubleValue();
                        bucketKey = String.format("%.1f", Math.max(1.0, Math.min(5.0, Math.round(d * 2.0) / 2.0)));
                    } else if (ratingObj != null) {
                        try {
                            double d = Double.parseDouble(ratingObj.toString());
                            bucketKey = String.format("%.1f", Math.max(1.0, Math.min(5.0, Math.round(d * 2.0) / 2.0)));
                        } catch (Exception ignore) {}
                    }

                    if (countObj instanceof Number) countVal = ((Number) countObj).intValue();
                    else if (countObj != null) {
                        try { countVal = Integer.valueOf(countObj.toString()); } catch (Exception ignore) {}
                    }

                    if (bucketKey != null && result.containsKey(bucketKey) && countVal != null) {
                        result.put(bucketKey, countVal);
                    } else {
                        log.warn("getDistribution row skipped aniId={}, row={}", aniId, row);
                    }
                }
            }
            log.debug("getDistribution final map aniId={} -> {}", aniId, result);
        } catch (Exception e) {
            log.error("getDistribution error aniId={}", aniId, e);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Double getAverage(Long aniId) {
        Double avg = null;
        try {
            avg = ratingQueryMapper.findAverageRatingByAnimeId(aniId);
        } catch (Exception e) {
            log.warn("getAverage failed aniId={}, error={}", aniId, e.toString());
        }
        return avg == null ? 0.0 : avg;
    }

    public void deleteMyRating(Long userId, Long aniId) {
        ratingRepository.deleteByUserIdAndAnimeId(userId, aniId);
        // 동기화: 애니의 집계 평점/카운트 갱신
        updateAnimeAggregates(aniId);
    }

    /**
     * ratings 테이블 기준으로 Anime.rating / Anime.ratingCount를 동기화한다.
     */
    private void updateAnimeAggregates(Long aniId) {
        try {
            Double avg = getAverage(aniId);
            Long cnt = ratingQueryMapper.countRatingsByAnimeId(aniId);
            Anime anime = animeRepository.findById(aniId).orElse(null);
            if (anime != null) {
                anime.setRating(avg == null ? 0.0 : avg);
                anime.setRatingCount(cnt == null ? 0 : cnt.intValue());
                animeRepository.save(anime);
                log.debug("Aggregates updated aniId={}, rating={}, ratingCount={}", aniId, anime.getRating(), anime.getRatingCount());
            }
        } catch (Exception e) {
            log.warn("updateAnimeAggregates failed aniId={}, error={}", aniId, e.toString());
        }
    }
}


