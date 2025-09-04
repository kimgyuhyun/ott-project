package com.ottproject.ottbackend.service; // 서비스 패키지

import com.ottproject.ottbackend.dto.FavoriteAnimeDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.entity.AnimeFavorite;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.mybatis.FavoriteQueryMapper;
import com.ottproject.ottbackend.repository.AnimeFavoriteRepository;
import com.ottproject.ottbackend.repository.AnimeRepository; // NEW
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * FavoriteAnimeService
 *
 * 큰 흐름
 * - 보고싶다 토글과 목록/상태 조회를 제공한다. 읽기 전용 질의는 MyBatis, CUD는 JPA를 사용한다.
 *
 * 메서드 개요
 * - toggle: 보고싶다 토글(on/off 반환)
 * - list: 내 보고싶다 목록 페이지 조회
 * - isFavorited: 개별 보고싶다 여부 조회
 */
@Service // 서비스 컴포넌트
@RequiredArgsConstructor // 생성자 주입
@Transactional // 쓰기 트랜잭션
public class FavoriteAnimeService { // 보고싶다 도메인 서비스
    private final AnimeFavoriteRepository favoriteRepository; // 보고싶다 JPA 리포지토리
    private final UserRepository userRepository; // 사용자 조회
    private final AnimeRepository animeListRepository; // 통합 Anime 리포지토리
    private final FavoriteQueryMapper favoriteQueryMapper; // 보고싶다 목록 조회(MyBatis)

    public boolean toggle(Long aniId, Long userId) { // 보고싶다 토글(true:on, false:off 반환)
        Optional<AnimeFavorite> existing = favoriteRepository.findByUser_IdAndAnime_Id(userId, aniId); // 기존 보고싶다 조회
        if (existing.isPresent()) { // 이미 보고싶다 추가됨
            favoriteRepository.deleteByUser_IdAndAnime_Id(userId, aniId); // 삭제로 off
            return false; // 현재 상태: off
        } // 미추가 상태
        User user = userRepository.findById(userId).orElseThrow(); // 사용자 존재 확인
        Anime ani = animeListRepository.findById(aniId).orElseThrow(); // 통합 Anime 조회
        AnimeFavorite entity = AnimeFavorite.builder().user(user).anime(ani).build(); // NEW 엔티티 구성
        favoriteRepository.save(entity); // 저장으로 on
        return true; // 현재 상태: on
    }

    @Transactional(readOnly = true) // 읽기 전용
    public PagedResponse<FavoriteAnimeDto> list(Long userId, int page, int size, String sort) { // 내 보고싶다 목록 조회
        System.out.println("🔧 [SERVICE] FavoriteAnimeService.list 시작");
        System.out.println("🔧 [SERVICE] 파라미터 - userId: " + userId + ", page: " + page + ", size: " + size + ", sort: " + sort);
        
        int limit = size; // limit 계산
        int offset = Math.max(page, 0) * size; // offset 계산
        System.out.println("🔧 [SERVICE] 계산된 limit: " + limit + ", offset: " + offset);
        
        System.out.println("🔧 [SERVICE] MyBatis 쿼리 호출 시작 - findFavoriteAnimesByUser");
        List<FavoriteAnimeDto> items = favoriteQueryMapper.findFavoriteAnimesByUser(userId, sort, limit, offset); // 항목 조회
        System.out.println("🔧 [SERVICE] MyBatis 쿼리 호출 완료 - findFavoriteAnimesByUser");
        
        System.out.println("🔧 [SERVICE] MyBatis 카운트 쿼리 호출 시작 - countFavoriteAnimesByUser");
        long total = favoriteQueryMapper.countFavoriteAnimesByUser(userId); // 총 개수 조회
        System.out.println("🔧 [SERVICE] MyBatis 카운트 쿼리 호출 완료 - countFavoriteAnimesByUser");
        
        System.out.println("🔧 [SERVICE] 조회 결과 - items 개수: " + items.size() + ", total: " + total);
        if (!items.isEmpty()) {
            System.out.println("🔧 [SERVICE] 첫 번째 아이템 상세:");
            FavoriteAnimeDto first = items.get(0);
            System.out.println("  - aniId: " + first.getAniId());
            System.out.println("  - title: " + first.getTitle());
            System.out.println("  - posterUrl: " + first.getPosterUrl());
            System.out.println("  - rating: " + first.getRating());
            System.out.println("  - favoritedAt: " + first.getFavoritedAt());
        } else {
            System.out.println("🔧 [SERVICE] 조회된 아이템이 없습니다.");
        }
        
        PagedResponse<FavoriteAnimeDto> response = new PagedResponse<>(items, total, page, size); // 페이지 응답 래핑
        System.out.println("🔧 [SERVICE] PagedResponse 생성 완료 - total: " + response.getTotal() + ", items: " + response.getItems().size());
        
        return response; // 페이지 응답 래핑
    }

    @Transactional(readOnly = true) // 읽기 전용
    public boolean isFavorited(Long aniId, Long userId) { // 개별 보고싶다 여부 조회
        if (userId == null) return false; // 비로그인 보호
        return favoriteRepository.existsByUser_IdAndAnime_Id(userId, aniId); // NEW 존재 여부 반환
    }
}