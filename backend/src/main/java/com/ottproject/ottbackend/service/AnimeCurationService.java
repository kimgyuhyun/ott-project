package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.admin.AdminAnimeListItemDto;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.curation.AnimeCurationQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * 관리자 애니 큐레이션 서비스
 *
 * 큰 흐름
 * - 운영자가 조건으로 애니를 찾고, 배지/노출 여부/제목/포스터를 고칠 수 있게 한다.
 * - 이 값들은 외부 API 가 주는 게 아니라 운영자가 판단하는 값이다.
 *
 * 트랜잭션(명시)
 * - 클래스 레벨 @Transactional 로 쓰기를 기본으로 두고, 읽기 메서드에만 readOnly=true 를 덧씌운다.
 *   PaymentCommandService/AdminContentService 와 같은 관례다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AnimeCurationService {

    private final AnimeCurationQueryRepository curationQueryRepository;
    private final AnimeRepository animeRepository;

    /**
     * 조건 검색.
     * 조건이 비어 있으면 전체를 돌려준다(검색에서는 정상이다 — 벌크에서만 위험해서 그쪽에서 따로 막는다).
     */
    @Transactional(readOnly = true)
    public PagedResponse<AdminAnimeListItemDto> search(AnimeCurationSearchCondition condition, int page, int size) {
        List<AdminAnimeListItemDto> items = curationQueryRepository.search(condition, page, size)
                .stream()
                .map(AdminAnimeListItemDto::from)
                .toList();
        long total = curationQueryRepository.countByCondition(condition);

        return new PagedResponse<>(items, total, page, size);
    }

    /**
     * 단건 조회(수정 폼용).
     */
    @Transactional(readOnly = true)
    public AdminAnimeListItemDto get(Long animeId) {
        return AdminAnimeListItemDto.from(loadOrThrow(animeId));
    }

    /**
     * 단건 큐레이션 수정.
     *
     * 여기서는 QueryDSL 을 쓰지 않는다 — 동적 쿼리가 아니라 ID 하나를 고르는 일이다.
     * findById 로 영속 엔티티를 가져와 세터로 바꾸면 더티 체킹이 커밋 시점에 UPDATE 를 만든다.
     * (save() 를 부르지 않는 것이 이 프로젝트 관례다: AdminContentService 참고)
     *
     * 잠금: AnimeRepository.findById 에는 @Lock(PESSIMISTIC_WRITE) 가 걸려 있어, 같은 작품을 동시에
     * 수정하려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다.
     *
     * @return 수정 결과
     */
    public AdminAnimeListItemDto update(Long animeId, AnimeCurationUpdateRequest request) {
        Anime anime = loadOrThrow(animeId);

        // 콘텐츠 필드: 값이 실제로 달라질 때만 반영하고, 그 경우에만 curated 를 켠다.
        boolean contentChanged = false;
        contentChanged |= applyIfChanged(request.getTitle(), anime.getTitle(), anime::setTitle);
        contentChanged |= applyIfChanged(request.getTitleEn(), anime.getTitleEn(), anime::setTitleEn);
        contentChanged |= applyIfChanged(request.getTitleJp(), anime.getTitleJp(), anime::setTitleJp);
        contentChanged |= applyIfChanged(request.getPosterUrl(), anime.getPosterUrl(), anime::setPosterUrl);

        // 배지/노출 여부: 보강이 건드리지 않는 값이라 curated 와 무관하다.
        if (request.getIsExclusive() != null) anime.setIsExclusive(request.getIsExclusive());
        if (request.getIsPopular() != null) anime.setIsPopular(request.getIsPopular());
        if (request.getIsNew() != null) anime.setIsNew(request.getIsNew());
        if (request.getIsActive() != null) anime.setIsActive(request.getIsActive());

        // 운영자가 콘텐츠를 실제로 고쳤을 때만 보강 제외 대상으로 표시한다.
        // 배지만 토글했다고 켜면 그 작품의 제목 보강이 영구히 막혀버린다.
        if (contentChanged) {
            anime.setCurated(Boolean.TRUE);
            log.info("애니 큐레이션으로 표시: ID {} - 이후 TMDB 자동 보강 제외", animeId);
        }

        return AdminAnimeListItemDto.from(anime);
    }

    /**
     * 부분 수정 적용: 요청값이 null 이면 건드리지 않고, 기존 값과 같으면 변경으로 치지 않는다.
     *
     * @return 실제로 값이 바뀌었는지 여부
     */
    private boolean applyIfChanged(String newValue, String currentValue, java.util.function.Consumer<String> setter) {
        if (newValue == null || Objects.equals(newValue, currentValue)) {
            return false;
        }
        setter.accept(newValue);
        return true;
    }

    private Anime loadOrThrow(Long animeId) {
        return animeRepository.findById(animeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "애니메이션을 찾을 수 없습니다."));
    }
}
