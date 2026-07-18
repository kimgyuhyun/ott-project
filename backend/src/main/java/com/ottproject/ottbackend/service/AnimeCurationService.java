package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.PagedResponse;
import com.ottproject.ottbackend.dto.admin.AdminAnimeDetailDto;
import com.ottproject.ottbackend.dto.admin.AdminAnimeListItemDto;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationPreviewResponse;
import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationRequest;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.dto.admin.AnimeCurationUpdateRequest;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.repository.AnimeRepository;
import com.ottproject.ottbackend.repository.curation.AnimeCurationQueryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    /** 벌크 미리보기에서 보여줄 표본 개수 — 조건이 의도대로인지 눈으로 확인할 정도면 충분하다 */
    private static final int BULK_PREVIEW_SAMPLE_SIZE = 10;

    private final AnimeCurationQueryRepository curationQueryRepository;
    private final AnimeRepository animeRepository;

    // 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 flush/clear 를 직접 제어해야 한다(applyBulkCuration 참고).
    @PersistenceContext
    private EntityManager entityManager;

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
     * 목록보다 넓은 상세 DTO 를 준다 — 줄거리처럼 목록에 싣기 무거운 값이 수정 대상이기 때문이다.
     *
     * 락 없는 조회를 쓰는 이유: 이 트랜잭션은 readOnly 라 PostgreSQL 이 SELECT ... FOR UPDATE 를 거부한다.
     * findById(락 O)를 쓰면 폼을 여는 것만으로 500 이 난다.
     */
    @Transactional(readOnly = true)
    public AdminAnimeDetailDto get(Long animeId) {
        return AdminAnimeDetailDto.from(loadForReadOrThrow(animeId));
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
    public AdminAnimeDetailDto update(Long animeId, AnimeCurationUpdateRequest request) {
        Anime anime = loadOrThrow(animeId);

        // 콘텐츠 필드: AnimeEnhancementService 가 덮어쓰는 필드와 정확히 같은 집합이다.
        // 값이 실제로 달라질 때만 반영하고, 그 경우에만 curated 를 켠다.
        boolean contentChanged = false;
        contentChanged |= applyIfChanged(request.getTitle(), anime.getTitle(), anime::setTitle);
        contentChanged |= applyIfChanged(request.getTitleEn(), anime.getTitleEn(), anime::setTitleEn);
        contentChanged |= applyIfChanged(request.getTitleJp(), anime.getTitleJp(), anime::setTitleJp);
        contentChanged |= applyIfChanged(request.getSynopsis(), anime.getSynopsis(), anime::setSynopsis);
        contentChanged |= applyIfChanged(request.getFullSynopsis(), anime.getFullSynopsis(), anime::setFullSynopsis);
        contentChanged |= applyIfChanged(request.getPosterUrl(), anime.getPosterUrl(), anime::setPosterUrl);
        contentChanged |= applyIfChanged(request.getBackdropUrl(), anime.getBackdropUrl(), anime::setBackdropUrl);

        // 배지/노출 여부: 보강이 건드리지 않는 값이라 curated 와 무관하다.
        if (request.getIsExclusive() != null) anime.setIsExclusive(request.getIsExclusive());
        if (request.getIsPopular() != null) anime.setIsPopular(request.getIsPopular());
        if (request.getIsNew() != null) anime.setIsNew(request.getIsNew());
        if (request.getIsCompleted() != null) anime.setIsCompleted(request.getIsCompleted());
        if (request.getIsSubtitle() != null) anime.setIsSubtitle(request.getIsSubtitle());
        if (request.getIsDub() != null) anime.setIsDub(request.getIsDub());
        if (request.getIsSimulcast() != null) anime.setIsSimulcast(request.getIsSimulcast());
        if (request.getIsActive() != null) anime.setIsActive(request.getIsActive());

        // 운영자가 콘텐츠를 실제로 고쳤을 때만 보강 제외 대상으로 표시한다.
        // 배지만 토글했다고 켜면 그 작품의 콘텐츠 보강이 영구히 막혀버린다.
        if (contentChanged) {
            anime.setCurated(Boolean.TRUE);
            log.info("애니 큐레이션으로 표시: ID {} - 이후 TMDB 자동 보강 제외", animeId);
        }

        return AdminAnimeDetailDto.from(anime);
    }

    /**
     * 벌크 수정 미리보기.
     *
     * 운영자가 실행 전에 "몇 건이 바뀌는지"를 확인하는 경로다. 여기서 본 건수를 벌크 요청의
     * expectedCount 로 되돌려 보내야 실행된다.
     */
    @Transactional(readOnly = true)
    public AnimeBulkCurationPreviewResponse previewBulkCuration(AnimeCurationSearchCondition condition) {
        requireNonEmptyCondition(condition);

        long affectedCount = curationQueryRepository.countByCondition(condition);
        List<AdminAnimeListItemDto> sample = curationQueryRepository.search(condition, 0, BULK_PREVIEW_SAMPLE_SIZE)
                .stream()
                .map(AdminAnimeListItemDto::from)
                .toList();

        return new AnimeBulkCurationPreviewResponse(affectedCount, sample);
    }

    /**
     * 조건 기반 벌크 큐레이션.
     *
     * 영속성 컨텍스트 취급(핵심)
     * - QueryDSL 벌크 UPDATE 는 영속성 컨텍스트를 거치지 않고 DB 로 바로 나간다. 그래서 앞뒤를 직접 맞춰야 한다.
     * - flush() 를 먼저: 이 트랜잭션에 더티 엔티티가 남아 있으면 벌크 UPDATE 가 먼저 나가고 커밋 시점의
     *   더티 체킹 UPDATE 가 나중에 덮어써서 벌크 결과를 되돌린다. 순서를 강제한다.
     * - clear() 를 나중에: 벌크는 1차 캐시를 갱신하지 않으므로, 이후 같은 엔티티를 읽으면 캐시의 옛 값이 나온다.
     *   전부 준영속으로 만들어 다음 조회가 DB 를 타게 한다.
     *   (부작용으로 컨텍스트 전체가 비워지지만, 이 메서드는 clear 이후 엔티티를 만지지 않는다)
     * - curated 는 건드리지 않는다. 벌크는 배지/노출 여부만 바꾸고 보강이 쓰는 콘텐츠 필드는 손대지 않는다.
     *
     * @return 실제로 바뀐 건수
     */
    public long applyBulkCuration(AnimeBulkCurationRequest request) {
        AnimeCurationSearchCondition condition = request.getCondition();

        // 안전장치 ①: 조건 없는 벌크 차단. 빈 조건은 곧 '전체 애니'이고, 카탈로그를 통째로 뒤집는 유일한 입구다.
        requireNonEmptyCondition(condition);

        // 안전장치 ②: 바꿀 값이 하나도 없는 요청 차단.
        if (!request.hasAnyChange()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경할 값이 없습니다.");
        }

        // 안전장치 ③: 미리보기에서 본 건수와 지금 건수가 다르면 중단.
        // 그 사이 동기화 배치가 행을 늘렸을 수 있고, 그러면 운영자가 승인한 것보다 많은 작품이 바뀐다.
        long actualCount = curationQueryRepository.countByCondition(condition);
        if (actualCount != request.getExpectedCount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "대상 건수가 미리보기와 다릅니다. 다시 확인해 주세요. (미리보기: "
                            + request.getExpectedCount() + ", 현재: " + actualCount + ")");
        }

        entityManager.flush(); // 대기 중인 더티 체킹 변경을 벌크보다 먼저 DB 에 반영
        long affected = curationQueryRepository.applyBulkCuration(condition, request);
        entityManager.clear(); // 벌크 결과와 어긋난 1차 캐시를 버린다

        log.info("애니 벌크 큐레이션 적용: {}건", affected);
        return affected;
    }

    private void requireNonEmptyCondition(AnimeCurationSearchCondition condition) {
        if (condition == null || condition.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "조건 없는 일괄 수정은 허용되지 않습니다. 조건을 하나 이상 지정해 주세요.");
        }
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

    /** 수정용 로딩 — 쓰기 락을 잡는다(동시 수정 방어). 쓰기 트랜잭션에서만 호출할 것. */
    private Anime loadOrThrow(Long animeId) {
        return animeRepository.findById(animeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "애니메이션을 찾을 수 없습니다."));
    }

    /** 조회용 로딩 — 락을 잡지 않는다. readOnly 트랜잭션에서 FOR UPDATE 가 나가면 PostgreSQL 이 거부한다. */
    private Anime loadForReadOrThrow(Long animeId) {
        return animeRepository.findByIdWithoutLock(animeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "애니메이션을 찾을 수 없습니다."));
    }
}
