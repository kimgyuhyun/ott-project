package com.ottproject.ottbackend.repository.curation;

import com.ottproject.ottbackend.dto.admin.AnimeBulkCurationRequest;
import com.ottproject.ottbackend.dto.admin.AnimeCurationSearchCondition;
import com.ottproject.ottbackend.entity.Anime;
import com.ottproject.ottbackend.enums.SyncOrigin;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static com.ottproject.ottbackend.entity.QAnime.anime;

/**
 * 관리자 애니 큐레이션 동적 검색 리포지토리 (QueryDSL)
 *
 * 큰 흐름
 * - 운영자가 자유 조합한 조건으로 Anime 엔티티를 검색한다. 조건 수가 9개라 조합이 수백 가지이고,
 *   런타임에 어떤 조합이 올지 알 수 없다. 타입 안전한 조립기가 이기는 유일한 지점이라 여기만 QueryDSL 을 쓴다.
 *
 * 경계(의도적)
 * - 이 클래스가 이 프로젝트에서 QueryDSL 이 존재하는 유일한 지점이다.
 * - AnimeRepository(JPA/더티 체킹)와 서로 모른다. Spring Data 커스텀 프래그먼트로 끼워넣지 않은 이유가 이것이다 —
 *   그렇게 하면 AnimeRepository 를 주입받는 모든 코드에 QueryDSL 표면이 딸려온다.
 * - 사용자향 읽기(AnimeQueryMapper 의 MyBatis 프로젝션)는 건드리지 않는다.
 *
 * 메서드 개요
 * - search: 조건에 맞는 엔티티 페이지 조회
 * - countByCondition: 조건에 맞는 전체 건수(페이지네이션/벌크 미리보기 공용)
 * - applyBulkCuration: 조건에 맞는 전체에 배지/노출 여부 일괄 적용
 */
@Repository
@RequiredArgsConstructor
public class AnimeCurationQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 조건에 맞는 애니를 최신 수정순으로 조회한다.
     * 정렬을 updatedAt 이 아니라 id 로 하는 이유: updatedAt 은 동시에 벌크로 갱신되면 값이 같아져
     * 페이지 경계에서 행이 누락/중복될 수 있다. id 는 항상 유일해 안정적인 정렬 기준이 된다.
     */
    public List<Anime> search(AnimeCurationSearchCondition condition, int page, int size) {
        return queryFactory
                .selectFrom(anime)
                .where(toPredicate(condition))
                .orderBy(anime.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
    }

    /**
     * 조건에 맞는 전체 건수.
     * 검색의 페이지네이션과 벌크 미리보기가 같은 술어를 공유해야, 미리보기에서 본 건수와
     * 실제 수정 대상이 어긋나지 않는다.
     */
    public long countByCondition(AnimeCurationSearchCondition condition) {
        Long total = queryFactory
                .select(anime.count())
                .from(anime)
                .where(toPredicate(condition))
                .fetchOne();
        return total == null ? 0L : total;
    }

    /**
     * 조건에 맞는 전체에 배지/노출 여부를 일괄 적용하고 영향 건수를 돌려준다.
     *
     * 벌크 연산의 묵시적 동작(반드시 알고 쓸 것)
     * - 이 UPDATE 는 영속성 컨텍스트를 거치지 않고 DB 로 바로 나간다. 따라서 1차 캐시가 갱신되지 않고,
     *   호출자(AnimeCurationService)가 실행 전 flush / 실행 후 clear 를 책임진다.
     * - @LastModifiedDate 는 영속성 컨텍스트의 라이프사이클 이벤트로 동작하므로 여기서는 발동하지 않는다.
     *   그대로 두면 updated_at 이 낡은 채 남아 "언제 바뀌었나"를 추적할 수 없다. 그래서 직접 세팅한다.
     * - Anime 에는 @Version 이 없어 낙관적 락 증가 문제는 없다.
     *
     * 조건이 비었는지는 여기서 막지 않는다(빈 조건 = 전체). 그 판단은 서비스의 안전장치가 한다.
     */
    public long applyBulkCuration(AnimeCurationSearchCondition condition, AnimeBulkCurationRequest request) {
        JPAUpdateClause update = queryFactory.update(anime).where(toPredicate(condition));

        if (request.getIsActive() != null) update.set(anime.isActive, request.getIsActive());
        if (request.getIsExclusive() != null) update.set(anime.isExclusive, request.getIsExclusive());
        if (request.getIsPopular() != null) update.set(anime.isPopular, request.getIsPopular());
        if (request.getIsNew() != null) update.set(anime.isNew, request.getIsNew());
        if (request.getIsCompleted() != null) update.set(anime.isCompleted, request.getIsCompleted());
        if (request.getIsSubtitle() != null) update.set(anime.isSubtitle, request.getIsSubtitle());
        if (request.getIsDub() != null) update.set(anime.isDub, request.getIsDub());
        if (request.getIsSimulcast() != null) update.set(anime.isSimulcast, request.getIsSimulcast());

        // Auditing 이 개입하지 않으므로 수정 시각을 직접 남긴다.
        update.set(anime.updatedAt, LocalDateTime.now());

        return update.execute();
    }

    /**
     * 조건 → 술어 변환.
     *
     * null 인 조건은 BooleanBuilder 가 무시하므로(null 을 and 하면 no-op) 조건이 없으면 전체가 대상이 된다.
     * 검색에서는 그게 맞지만 벌크에서는 위험하다 — 그래서 벌크 차단은 서비스에서
     * AnimeCurationSearchCondition.isEmpty() 로 따로 막는다.
     */
    BooleanBuilder toPredicate(AnimeCurationSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();
        if (condition == null) {
            return builder;
        }
        builder.and(titleContains(condition.getTitleKeyword()));
        builder.and(condition.getStatus() == null ? null : anime.status.eq(condition.getStatus()));
        builder.and(condition.getYear() == null ? null : anime.year.eq(condition.getYear()));
        builder.and(condition.getIsActive() == null ? null : anime.isActive.eq(condition.getIsActive()));
        builder.and(condition.getIsExclusive() == null ? null : anime.isExclusive.eq(condition.getIsExclusive()));
        builder.and(condition.getIsPopular() == null ? null : anime.isPopular.eq(condition.getIsPopular()));
        builder.and(condition.getIsNew() == null ? null : anime.isNew.eq(condition.getIsNew()));
        builder.and(condition.getCurated() == null ? null : anime.curated.eq(condition.getCurated()));
        builder.and(syncOriginMatches(condition.getSyncOrigin()));
        return builder;
    }

    /**
     * 한/영/일 제목 통합 부분일치.
     * 운영자는 어느 언어의 제목이 채워져 있는지 모르는 채 검색하므로 셋 중 하나만 맞아도 잡아야 한다.
     */
    private BooleanExpression titleContains(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null; // 조건 없음
        }
        String trimmed = keyword.trim();
        return anime.title.containsIgnoreCase(trimmed)
                .or(anime.titleEn.containsIgnoreCase(trimmed))
                .or(anime.titleJp.containsIgnoreCase(trimmed));
    }

    /**
     * 유입 경로 필터.
     * 전용 컬럼이 없으므로 malId 유무로 판정한다(SyncOrigin 주석 참고).
     */
    private BooleanExpression syncOriginMatches(SyncOrigin syncOrigin) {
        if (syncOrigin == null) {
            return null; // 조건 없음
        }
        return syncOrigin == SyncOrigin.JIKAN ? anime.malId.isNotNull() : anime.malId.isNull();
    }
}
