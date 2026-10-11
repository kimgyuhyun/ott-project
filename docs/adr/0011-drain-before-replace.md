# 0011. 롤링 교체 전에 인스턴스를 nginx 에서 뺀다(drain)

- 상태: 채택 (2026-10-09)
- 관련: `deploy-drain.ps1`, `deploy-rolling.ps1`, `nginx/nginx.prod.ha.conf`, `docs/deployment.md`,
  `loadtest/keepalive/README.md`, [0002](0002-rolling-deploy-sequential.md)

## 배경

[0002](0002-rolling-deploy-sequential.md) 는 순차 교체 + `proxy_next_upstream` 으로 무중단을 만들었고,
그 근거는 "374요청 0건 실패"였다. nginx→앱 keepalive 를 넣기 전에 롤링이 여전히 무실패인지 다시 재다가,
**keepalive 와 상관없이** 현행 절차에서도 502 가 나는 것을 찾았다.

| 30 r/s 지속 부하, 순차 교체 3회 | 실패 |
|---|---|
| 현행 설정 | 51 / 0 / 0 |
| keepalive 설정 | 27 / 0 / 51 |

원인은 두 쪽이 "살아 있다"를 다르게 판단하는 데 있다.

- 스크립트는 새 인스턴스의 health 를 **직접** 물어 UP 이면 바로 다음 인스턴스를 내린다
- nginx 는 재기동 중 실패한 인스턴스를 **마지막 실패 후 `fail_timeout`(5s)** 동안 후보에서 뺀다

그래서 최대 5초 동안 두 서버가 모두 후보에서 빠지고 `no live upstreams` 502 가 난다. 마지막 실패 시점이
nginx 의 재시도 주기에 따라 0~5초 사이에서 무작위라 실패가 났다 안 났다 한다. 에러 로그 타이밍으로
세 회차 모두 겹침 여부와 결과가 일치했다. 0002 의 0건도 이 창을 피한 회차였다.

## 결정

교체 직전에 그 인스턴스를 nginx upstream 에서 `down` 으로 표시하고 reload 한다. 다음 인스턴스를 뺄 때
앞 인스턴스를 같은 reload 로 되돌리고, 루프가 끝나면 전부 되돌린다. 실패하면 catch 에서 전부 되돌린다.

- reload 는 nginx 의 실패 기록도 지우므로, 되돌린 인스턴스는 바로 후보가 된다
- 교체 중인 인스턴스로 요청이 아예 가지 않아 재시도에 기대지 않는다
- 0002 의 결정(순차 교체)은 그대로다. 이 ADR 은 그 안의 한 단계를 바꾼다

| 30 r/s, 3회 | 실패 | 다른 인스턴스로 재시도 |
|---|---|---|
| 현행 | 51 / 0 / 0 | 19 / 16 / 17 |
| **drain** | **0 / 0 / 0** | **0 / 0 / 0** |
| drain + keepalive | 0 / 0 / 0 | 0 / 0 / 0 |

## 대안

| 대안 | 판단 |
|---|---|
| health UP 후 6초(= fail_timeout + 1s) 대기 | **기각.** 실측 0/0/0 이지만 대기 시간이 nginx `fail_timeout` 값에 묶여 한쪽만 바꾸면 다시 깨진다. 내려가는 인스턴스로 요청이 가는 것 자체는 막지 못해 재시도(회당 15~20건)에 계속 기댄다. POST 는 이미 보낸 뒤 실패하면 재시도되지 않는다 |
| `fail_timeout` 을 줄이기 | **기각.** 롤링이 아닌 실제 장애 때의 동작까지 바뀐다. 창이 줄 뿐 없어지지 않는다 |
| `max_fails=0`(실패 표시를 끔) | **기각.** 연결 거부가 아니라 타임아웃으로 실패하면 요청마다 `proxy_connect_timeout`(2s)이 붙는다 |
| `proxy_next_upstream non_idempotent` | **기각.** 결제 같은 POST 가 두 번 처리될 수 있다 |
| nginx 동적 upstream API | 불가. 오픈소스 nginx 에는 없다(nginx Plus 기능) |

## 결과

- 배포당 nginx reload 가 3회 늘어난다. reload 때 이전 워커가 클라이언트 쪽 유휴 keep-alive 연결을
  닫는데, 그 순간 그 연결로 보낸 요청은 EOF 를 받는다. reload 를 4회로 했던 시험에서 3회 중 1건(HTTP/1.1
  POST)이 그랬고, 다음 drain 이 앞 복구를 겸하게 해 3회로 줄였다. 운영의 클라이언트→nginx 는 HTTP/2 라
  양상이 다를 수 있다(검증 안 함)
- nginx 설정 변경이 배포 마지막이 아니라 첫 drain reload 시점에 적용된다
- `deploy-drain.ps1` 은 upstream 의 `server <이름>:8090 ...;` 줄 형식에 의존한다. 형식을 바꾸면 배포가
  drain 단계에서 멈춘다(설정 파일에 경고를 적어 둠)
- 2026-10-09 운영 첫 배포 두 번에서 로그 순서, 배포 후 설정 원상 복구, 배포 구간 nginx 오류·5xx 0건을
  확인했다. 실사용 트래픽이 없는 시간이라 "실패가 없었다"가 아니라 "관측된 문제가 없다"이다
