# 배포 가이드

## Docker 배포

### 컨테이너 구성
- **Nginx**: 리버스 프록시, SSL 터미네이션
- **Backend**: Spring Boot 애플리케이션 (포트 8090)
- **Frontend**: Next.js 애플리케이션 (포트 3000)
- **PostgreSQL**: 데이터베이스 (포트 5432)
- **Redis**: 캐시 서버 (포트 6379)
- **Kafka**: 결제 부수효과 이벤트 스트림 (KRaft 단일 노드). `docs/kafka-outbox.md` 참고
- **RabbitMQ**: 정기결제 실패 재시도 지연 큐 (TTL+DLX)

### 포트 설정
- **공개 포트**: 80 (HTTP), 443 (HTTPS)만 외부 노출
- **내부 통신**: 백엔드(8090), 프론트(3000), DB(5432), Redis(6379), Kafka, RabbitMQ는 컨테이너 내부 통신만 사용

### 실행 명령 (중요)
- **운영 배포(권장)**: `.\deploy.ps1` — 아래 4개 파일을 고정으로 묶어 egress 차단과 모니터링까지 적용
  - `docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.netlock.yml -f docker-compose.monitoring.yml up -d`
  - monitoring 파일을 빼고 올리면 `--remove-orphans` 가 모니터링 스택을 걷어낸다. 반드시 함께 묶을 것.
- **개발**: `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
- **주의**: 플래그 없는 `docker compose up` 은 배포 명령이 아니다.
  netlock(egress 차단)이 빠져 프론트 인터넷 접근이 다시 열리므로 사용하지 말 것.
- **환경 변수**: `env.example` 참고. 실제 값은 `.env.enc`(SOPS+age)에서 복호화해 `.env` 로 주입.

### 무중단 배포
- **스크립트**: `.\deploy-rolling.ps1` — 백엔드 2인스턴스(`docker-compose.ha.yml`)를 **순차 교체**한다
- **인스턴스를 2개로 늘리는 것만으로는 무중단이 되지 않는다.** 두 인스턴스를 동시에 재생성하면
  교체 구간이 그대로 노출된다 (실측: 동시 재기동 시 197요청 중 164건 502)
- 순차 교체 + nginx `proxy_next_upstream` 을 함께 적용해야 성립한다 (실측: 374요청 0건 실패)
- ha 오버레이를 맨손 `up` 으로 올리면 동시 재생성이 되므로 반드시 스크립트를 쓸 것

### compose 파일 구성
| 파일 | 용도 | 배포 시 사용 |
|---|---|---|
| `docker-compose.yml` | 기본 서비스 정의 | O |
| `docker-compose.prod.yml` | 운영 오버레이 | O |
| `docker-compose.netlock.yml` | egress 차단·망분리 | O |
| `docker-compose.monitoring.yml` | Prometheus/Grafana/Loki | O |
| `docker-compose.dev.yml` | 개발용 오버레이 | X |
| `docker-compose.ha.yml` | 백엔드 2인스턴스 오버레이 (무중단 배포용) | 필요 시 |
| `docker-compose.multi.yml` | **실험 전용** 독립 스택. 다중 인스턴스에서만 드러나는 문제 관찰용 | X |
| `docker-compose.certbot.yml` | 인증서 발급 시에만 임시 기동 | X |
| `docker-compose.pgadmin.yml` | pgAdmin GUI (opt-in). netlock 유지한 채 DB 접근 | X |

## CI/CD

### GitHub Actions
- **배포 방식**: Self-hosted runner 사용
- **보안**: SSH(22) 포트는 닫고 GitHub Actions로만 배포
- **레지스트리**: GitHub Container Registry(ghcr.io, 비공개). CD가 커밋 이미지를 digest로 고정해 배포
  (구 Docker Hub `para98` 는 침해 이력으로 폐기)

## SSL/TLS 설정

### Let's Encrypt
- **인증서**: 자동 갱신 설정
- **프로토콜**: TLSv1.2, TLSv1.3 지원
- **사이퍼 스위트**: 안전한 암호화 알고리즘 적용

### Nginx 설정
- **설정 파일**: `nginx/nginx.prod.conf`
- **보안 헤더**: HSTS, CSP, XFO, XCTO, Referrer-Policy, Permissions-Policy
- **레이트 리밋**: `limit_req` 모듈 적용
