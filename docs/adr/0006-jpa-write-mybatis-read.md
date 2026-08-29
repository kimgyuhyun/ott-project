# 0006. 쓰기는 JPA, 조회는 MyBatis 로 나눈다

- 상태: 채택 (2025년경) — 근거가 사후에 재평가됨
- 관련: `ARCHITECTURE 3`

## 배경

**채택 당시의 근거는 외부 권고였고, 자체 측정이나 대안 비교는 없었다.**

"CUD 는 JPA, R 은 MyBatis 로 나눠라. 복잡한 필터링은 MyBatis 가 낫다"는 조언을 받아 그대로
적용했다. 이 ADR 은 그 결정을 정당화하려고 쓰는 게 아니라, **1년 뒤 코드를 실제로 세어보고
그 권고가 어디까지 맞았는지 기록**하기 위해 쓴다.

## 결정

- 쓰기(INSERT/UPDATE/DELETE)는 JPA 리포지터리
- 조회는 MyBatis 매퍼 XML (`backend/src/main/resources/mappers/`)

## 대안

| 대안 | 판단 |
|---|---|
| JPA 단독 (+ `@Query`/fetch join) | **검토하지 않음** |
| QueryDSL | **검토하지 않음** |

당시 두 가지를 비교한 기록이 없다. 권고를 받아들인 것이 결정 과정의 전부였다.

## 1년 뒤 실측

| 항목 | 수 |
|---|---|
| JPA 리포지터리 | 37 |
| MyBatis 매퍼 | 15 |

동적 SQL 태그(`<if>`/`<choose>`/`<foreach>`/`<where>`/`<trim>`) 사용량은 **심하게 편중돼 있다.**

| 매퍼 | 동적 태그 | select |
|---|---|---|
| `AnimeQueryMapper` | 21 | 22 |
| `SearchQueryMapper` | 6 | 3 |
| 나머지 13개 | 0~3 | — |
| 그중 6개 (`RatingQuery`, `MypageActivityQuery`, `MembershipQuery`, `Episode`, `FavoriteQuery`, `MypageStatsQuery`, `BingeWatch`) | **0** | — |

`AnimeQueryMapper` 는 선택적 조건이 14개 이상이다 — 장르·태그·연도·분기·방영상태·타입·최소평점·
더빙/자막/독점/신작/인기/완결 여부, 그리고 로그인 사용자 여부에 따른 분기. **런타임 조건 조합이
실제로 존재하고, 여기서는 권고가 맞았다.**

반면 동적 태그가 0개인 6개 매퍼는 조건이 고정된 정적 select 다. **이쪽은 "복잡한 필터링" 근거가
성립하지 않고, 매퍼를 만드는 게 관성이 된 결과다.**

즉 실제로 작동한 기준은 "CUD 냐 R 이냐"가 아니라 **"런타임 조건 조합이 있느냐"** 였다.
원래 권고의 축과 실제로 유효했던 축이 다르다.

## 쓰기 예외 1건

`PlayerProgressQueryMapper` 만 MyBatis 로 쓰기를 한다 (`episode_progress` 배치 upsert,
`mergeProgress`, 배치 update/delete). 재생 진행률 버퍼를 주기적으로 flush 하는 경로라 건별
저장이 아니라 배치 upsert 가 필요했다.

`episode_progress` 는 **쓰기 경로를 MyBatis 하나로 통일**했다. `EpisodeProgressRepository`
와 `PlayerService` 양쪽에 그 근거가 주석으로 남아 있다. 같은 테이블 쓰기가 두 수단으로 갈리는
상태는 아니다.

## 결과

- 매핑 규칙이 둘로 갈린다. 엔티티 필드를 바꾸면 JPA 는 컴파일 타임에 드러나지만 **매퍼 XML 은
  런타임에 터진다.** 이게 이 결정의 실제 비용이다
- 데이터 접근 수단 선택 기준은 `ARCHITECTURE 3` 을 따른다. 이 프로젝트는 규칙 문서보다 먼저
  만들어졌으므로 현재 구성은 그 이전 결정이고, 기존 상태로 남긴다
- **새 조회를 추가할 때 자동으로 매퍼를 만들지 않는다.** 런타임 조건 조합이 없으면 JPA 쪽이
  맞다. 위 실측의 6개가 그 반례다
