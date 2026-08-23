# 조사 요청: backend CI 잡이 Gradle 빌드를 두 번 한다

`C:\solo-project\ott-project` 에서 시작한다. 아직 아무것도 고치지 말고, 먼저 조사해서
선택지와 트레이드오프를 보고해라. 어느 쪽으로 갈지는 내가 정한다.

## 무엇이 문제인가

`.github/workflows/ci.yml` 의 `backend` 잡이 파이프라인에서 가장 길다.
실측으로 전체 514초 중 395초(77%)를 차지한다. 그런데 이 잡이 Gradle 빌드를 두 번 돌린다.

1. 러너에서 `./gradlew clean build` — 컴파일 + 테스트 525건 + spotless + spotbugs
2. `docker build` 안에서 `backend/Dockerfile` 이 다시:
   - `RUN ./gradlew dependencies --no-daemon` (Dockerfile:21)
   - `RUN ./gradlew build -x test --no-daemon` (Dockerfile:27)

두 번째는 Gradle 배포판부터 다시 받는다. CI 로그에서
`Build & Push backend image ... Downloading https://services.gradle.org/distributions/gradle-8.14.5-bin.zip`
로 확인된다.

`gradle/actions/setup-gradle` 로 러너 쪽 캐시를 붙여봤지만 1번만 덮어서 효과가 -15초에 그쳤다
(커밋 `e88e136`, run 32639629691). 캐시 히트 자체는 로그로 확인됐다.

## 조사해서 알려줄 것

1. **두 번째 빌드가 실제로 몇 초인가.** `gh` CLI 가 `C:\Program Files\GitHub CLI\gh.exe` 에 있다.
   `gh run view <id> --json jobs` 로 스텝 단위 시간을 볼 수 있다. backend 잡의
   "Build & Push backend image" 스텝 시간을 최근 여러 run 에서 뽑아 편차까지 같이 봐라.
   러너 편차가 크다(같은 코드로 27초↔43초를 봤다). 1회 측정으로 결론 내지 마라.

2. **선택지별 트레이드오프.** 최소한 이 셋은 다뤄라.
   - (a) 러너가 만든 jar 를 `COPY` 하고 Dockerfile 에서 빌드를 없앤다
   - (b) `docker/build-push-action` + `cache-from/to: type=gha` 로 도커 레이어를 캐시한다
   - (c) 그대로 둔다

3. **(a) 를 택하면 무엇을 잃는가.** 지금 `backend/Dockerfile` 은 자기 완결적이다 —
   저장소만 있으면 `docker build` 하나로 앱이 만들어진다. jar 를 밖에서 받아오면 그 성질이 사라진다.
   그게 이 프로젝트에서 실제로 문제가 되는 상황이 있는지 확인해라. 특히:
   - CD(`cd.yml`)는 이미지를 ghcr 에서 digest 로 당겨 쓰므로 영향이 없어 보이는데 맞는지
   - 로컬 수동 배포(`deploy.ps1`, `deploy-rolling.ps1`)가 `ott-backend:clean` 같은 로컬 빌드
     이미지를 기대하는 경로가 있는지(`docker-compose.netlock.yml` 의 `APP_IMAGE` 기본값 참고)
   - `_incident_2026-06-20/` 과 `docker-compose.netlock.yml` 헤더에 "클린 이미지" 개념이 있는데
     그 절차가 Dockerfile 단독 빌드에 의존하는지

## 지켜야 할 것

- 규칙 문서는 `C:\dev-standards\standards\` 에 있다. 이 작업은 PLATFORM 6·7·8절에 걸린다.
  특히 8절의 "[절대] 이미지 태그는 커밋 SHA 로 찍는다"와 7절의 CI 순서 조항을 확인해라.
- 프로덕션 스택이 이 호스트에서 돌고 있다. `docker compose up` 은 훅이 막는다(의도된 가드).
  검증이 필요하면 `docker-compose.e2e.yml` 처럼 `-p` 로 프로젝트를 분리한 독립 스택을 쓴다.
- 측정 없이 "빨라질 것이다"라고 쓰지 마라. 실측치와 편차를 같이 보고해라.

## 배경 (참고)

이 조사는 CI 시간을 재던 중에 나왔다. 당시 결론은 "8분 34초는 정상 범위이고 러너 편차가
±40%라 -15초를 쫓는 건 의미가 없다"였다. 즉 급한 일이 아니다.
그래도 이 이중 빌드는 편차보다 큰 폭일 가능성이 있어서 따로 확인하려는 것이다.
기대 효과가 편차 안에 들어간다면 (c) 를 권해도 된다 — 그게 정답이면 그렇게 말해라.
