-- 부하 테스트 계정 및 그로 인해 생성된 데이터 정리
-- episode_progress 는 users FK 가 ON DELETE CASCADE 라 같이 지워진다.

DELETE FROM users WHERE email LIKE 'loadtest%@loadtest.local';

SELECT count(*) AS remaining FROM users WHERE email LIKE 'loadtest%@loadtest.local';
