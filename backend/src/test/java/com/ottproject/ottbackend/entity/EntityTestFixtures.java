package com.ottproject.ottbackend.entity;

/**
 * 테스트 픽스처용 빈 엔티티 생성기.
 *
 * 왜 필요한가
 * - 엔티티의 기본 생성자는 protected 라 다른 패키지의 테스트가 new 로 만들 수 없다.
 *   이 클래스는 엔티티와 같은 패키지에 있어(테스트 소스셋) 그 생성자에 닿는다.
 * - 정적 팩토리로 만들 수 있는 것은 팩토리를 쓴다. 여기 있는 것은 팩토리를 픽스처로 쓰기
 *   어려운 경우다 — Anime.createAnime 은 인자가 31개고, Episode 는 슬라이스 테스트가
 *   Auditing 없이 not-null 시각 컬럼을 직접 채워야 한다.
 *
 * 프로덕션 코드에서는 쓰지 않는다. 프로덕션 생성 경로는 엔티티의 정적 팩토리다.
 */
public final class EntityTestFixtures {

    private EntityTestFixtures() {
    }

    /** 필드가 비어 있는 Anime — 호출자가 필요한 컬럼만 채운다. */
    public static Anime emptyAnime() {
        return new Anime();
    }

    /** 필드가 비어 있는 Episode — 호출자가 필요한 컬럼만 채운다. */
    public static Episode emptyEpisode() {
        return new Episode();
    }
}
