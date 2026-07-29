# ott-hls-edge (Cloudflare Worker)

R2 HLS 버킷 앞단에서 secure_link 서명(`e`/`st`)을 검증하는 접근 게이트.

## 동작

1. 백엔드(`PlaybackAuthService`)가 `master.m3u8` URL 하나만 서명해 발급.
2. Worker 가 진입 요청의 `e`/`st` 를 검증(만료·서명).
3. 통과하면 R2 에서 객체를 서빙. 응답이 `.m3u8` 이면 하위 URI(자식
   재생목록·세그먼트)에 **Worker 가 직접 서명을 붙여 되쓴다**(캐스케이드).
   → 세그먼트마다 백엔드 서명 불필요, 쿠키/경로깊이 규칙 불필요.

서명 포맷은 백엔드 `HlsSignedUrlUtil` 과 동일:
`st = urlsafe-base64-nopad( MD5( expires + uriPath + " " + secret ) )`

## 배포

```bash
npm i -g wrangler          # 최초 1회
wrangler login             # 브라우저로 CF 계정 인증(네임서버 등록 불필요)

# wrangler.toml 의 bucket_name 을 실제 HLS 버킷명으로 확인/수정

wrangler secret put SECURE_LINK_SECRET   # 백엔드 .env 값과 동일하게 입력
wrangler deploy
```

배포 후 주소: `https://ott-hls-edge.<계정>.workers.dev` (커스텀 도메인 불필요).

## 레포 밖 전제 (적용 완료, 깨지면 안 되는 것)

- R2 버킷 **공개 접근(r2.dev / Public URL) 차단** — Worker 가 유일한 경로.
  이게 다시 켜지면 서명 전체가 무력화된다.
- DB `episodes.video_url` 베이스가 Worker 주소를 가리킨다.
- `SECURE_LINK_SECRET` 이 백엔드 `.env.enc` 와 Worker secret 양쪽에 동일 값으로 존재.
  한쪽만 바꾸면 전 재생이 403 이 된다.

설계 배경과 캐스케이드를 택한 이유는 `docs/streaming.md`.

## 로컬 확인

`wrangler dev` 로 띄운 뒤, 백엔드가 발급한 `master.m3u8?e=..&st=..` 를 그대로
요청하면 200 + 되쓰인 플레이리스트, 위조/만료 토큰은 403 이 나와야 한다.
