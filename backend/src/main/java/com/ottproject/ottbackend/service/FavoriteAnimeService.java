package com.ottproject.ottbackend.service; // 서비스 패키지

import com.ottproject.ottbackend.dto.FavoriteAnimeDto;
import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.entity.AnimeFavorite;
import com.ottproject.ottbackend.entity.AnimeList;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.mybatis.FavoriteQueryMapper;
import com.ottproject.ottbackend.repository.AnimeFavoriteRepository;
import com.ottproject.ottbackend.repository.AnimeListRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service // 서비스 컴포넌트
@RequiredArgsConstructor // 생성자 주입
@Transactional // 쓰기 트랜잭션
public class FavoriteAnimeService { // 찜 도메인 서비스
    private final AnimeFavoriteRepository favoriteRepository; // 찜 JPA 리포지토리
    private final UserRepository userRepository; // 사용자 조회
    private final AnimeListRepository animeListRepository; // 애니 조회 리포지토리
    private final FavoriteQueryMapper favoriteQueryMapper; // 찜 목록 조회(MyBatis)

    public boolean toggle(Long aniId, Long userId) { // 찜 토글(true:on, false:off 반환)
        Optional<AnimeFavorite> existing = favoriteRepository.findByUserIdAndAniListId(userId, aniId); // 기존 찜 조회
        if (existing.isPresent()) { // 이미 찜됨
            favoriteRepository.deleteByUserIdAndAniListId(userId, aniId); // 삭제로 off
            return false; // 현재 상태: off
        } // 미찜 상태
        User user = userRepository.findById(userId).orElseThrow(); // 사용자 존재 확인
        AnimeList ani = animeListRepository.findById(aniId).orElseThrow(); // 애니 존재 확인
        AnimeFavorite entity = AnimeFavorite.builder().user(user).animeList(ani).build(); // 엔티티 구성
        favoriteRepository.save(entity); // 저장으로 on
        return true; // 현재 상태: on
    }

    @Transactional(readOnly = true) // 읽기 전용
    public PagedResponse<FavoriteAnimeDto> list(Long userId, int page, int size, String sort) { // 내 찜 목록 조회
        int limit = size; // limit 계산
        int offset = Math.max(page, 0) * size; // offset 계산
        List<FavoriteAnimeDto> items = favoriteQueryMapper.findFavoriteAnimesByUser(userId, sort, limit, offset); // 항목 조회
        long total = favoriteQueryMapper.countFavoriteAnimesByUser(userId); // 총 개수 조회
        return new PagedResponse<>(items, total, page, size); // 페이지 응답 래핑
    }

    @Transactional(readOnly = true) // 읽기 전용
    public boolean isFavorited(Long aniId, Long userId) { // 개별 찜 여부 조회
        if (userId == null) return false; // 비로그인 보호
        return favoriteRepository.existsByUserIdAndAniListId(userId, aniId); // 존재 여부 반환
    }
}