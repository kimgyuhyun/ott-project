package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.Rating;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.mybatis.RatingQueryMapper;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.RatingRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        // 서버측 범위 검증: 1.0~5.0, 0.5 단위만 허용(집계 평점/분포 오염 방지)
        if (score == null || score < 1.0 || score > 5.0 || Math.abs(score * 2 - Math.rint(score * 2)) > 1e-9) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "평점은 1.0~5.0 사이 0.5 단위여야 합니다.");
        }
        User user = userRepository.getReferenceById(userId); // FK 바인딩만 필요하므로 프록시로 충분
        // 락 없는 조회를 쓴다: 여기서 애니는 Rating 의 FK 를 채우는 용도일 뿐이고, 집계는 아래에서 조건부 UPDATE 로 따로 간다.
        // findById(PESSIMISTIC_WRITE)를 쓰면 별점 하나 남기는 동안 애니 행에 쓰기 락이 걸려 관리자 수정이 대기한다.
        Anime anime = animeRepository.findByIdWithoutLock(aniId).orElseThrow();

        Rating rating = ratingRepository
                .findByUserIdAndAnimeId(userId, aniId)
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
                        } catch (Exception ignore) {
                        }
                    }

                    if (countObj instanceof Number) countVal = ((Number) countObj).intValue();
                    else if (countObj != null) {
                        try {
                            countVal = Integer.valueOf(countObj.toString());
                        } catch (Exception ignore) {
                        }
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
            // 집계 컬럼만 바꾸는 UPDATE 다. 엔티티를 읽어 세터로 고치면 @Version 과 updatedAt 까지 올라가서,
            // 별점 하나에 관리자의 열린 큐레이션 폼이 409 로 거절된다(AnimeRepository.updateRatingAggregates 주석).
            int updated = animeRepository.updateRatingAggregates(
                    aniId, avg == null ? 0.0 : avg, cnt == null ? 0 : cnt.intValue());
            log.debug("Aggregates updated aniId={}, rating={}, ratingCount={}, rows={}", aniId, avg, cnt, updated);
        } catch (Exception e) {
            log.warn("updateAnimeAggregates failed aniId={}, error={}", aniId, e.toString());
        }
    }
}
