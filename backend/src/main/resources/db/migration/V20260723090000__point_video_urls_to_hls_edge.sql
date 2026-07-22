-- HLS 서빙을 R2 공개 URL → Cloudflare Worker(서명 검증 엣지)로 전환
--
-- 배경
-- - 기존: episodes.video_url 이 R2 공개 호스트(pub-...r2.dev)를 직접 가리켜
--   서명(e/st)이 검증되지 않았다 — URL 만 알면 누구나 다운로드 가능.
-- - 변경: ott-hls-edge Worker(workers.dev)가 secure_link 서명을 검증한 뒤
--   R2 바인딩으로 서빙한다. 경로(/sintel/...)는 동일해 호스트만 교체한다.
-- - 이 전환과 함께 R2 버킷의 공개 접근(r2.dev)을 꺼야 우회 다운로드가 막힌다.
--
-- REPLACE + WHERE 로 해당 호스트를 가리키는 행만 안전하게 교체한다.
UPDATE episodes
SET video_url = REPLACE(
        video_url,
        'https://pub-62dbc9fd20414f4ca7be13a798b87bcf.r2.dev',
        'https://ott-hls-edge.kimgyuhyun.workers.dev')
WHERE video_url LIKE 'https://pub-62dbc9fd20414f4ca7be13a798b87bcf.r2.dev/%';
