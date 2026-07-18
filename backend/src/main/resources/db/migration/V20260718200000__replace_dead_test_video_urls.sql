-- 죽은 테스트 영상 URL 교체
--
-- 배경
-- V2025_08_37 이 심어둔 http://commondatastorage.googleapis.com/gtv-videos-bucket/... 은
-- 구글이 버킷의 익명 접근을 닫으면서 403 AccessDenied 가 됐다. 코드/배포와 무관하게 어느 날부터
-- 전 에피소드가 재생되지 않는다(다운로드는 브라우저가 직접 하므로 서버 로그에 흔적이 남지 않는다).
--
-- 교체 대상 URL 의 조건
-- - https: 배포가 https 라 http 영상은 브라우저가 차단하거나 승격 후 실패한다(기존 값이 http 였다).
-- - Range 요청 지원(206): 없으면 탐색과 이어보기 진행률이 동작하지 않는다.
-- - 라이선스: Blender Foundation 오픈 무비(Big Buck Bunny), CC BY 3.0 — 출처 표시 조건.
--
-- V2025_08_37 을 고치지 않고 새 마이그레이션으로 두는 이유
-- - 이미 적용된 마이그레이션을 수정하면 Flyway 체크섬이 어긋나 앱이 기동하지 못한다.

UPDATE episodes
SET video_url = 'https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4'
WHERE video_url = 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';
