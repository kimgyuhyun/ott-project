-- 인덱스가 없는 외래 키 컬럼 10개에 인덱스를 만든다.
-- PostgreSQL 은 외래 키에 인덱스를 자동 생성하지 않는다. 부모 행을 지우거나 키를 갱신하면
-- 참조 무결성 트리거가 자식 테이블을 전부 훑는다. 자식이 여럿이면 부모 삭제 한 건에
-- 풀스캔이 자식 수만큼 붙는다. users 삭제가 여기 해당한다(자식 6개가 무인덱스였다).
--
-- 대상은 pg_constraint 와 pg_index 를 대조해 "선두 컬럼이 그 FK 컬럼인 인덱스가 하나도 없는"
-- 것만 골랐다. 이미 복합 인덱스의 선두 컬럼인 것(예: ani_favorites.user_id 는
-- (user_id, ani_id) 유니크의 선두)은 중복이므로 만들지 않는다.
--
-- CONCURRENTLY 를 쓰므로 이 파일은 트랜잭션 밖에서 실행된다
-- (같은 이름의 .sql.conf 에 executeInTransaction=false).
-- 중단되면 무효 인덱스가 남을 수 있다. 재적용 전에 확인할 것:
--   SELECT indexrelid::regclass FROM pg_index WHERE NOT indisvalid;
-- IF NOT EXISTS 는 무효 인덱스도 "있다"고 보므로, 무효 인덱스는 먼저 DROP 해야 한다.

-- 부모: anime. 작품 삭제 시 찜 정리, 작품별 찜 수 집계.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ani_favorites_ani_id
  ON ani_favorites (ani_id);

-- 부모: studios. PK 가 (anime_id, studio_id) 라 studio_id 는 선두가 아니다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_anime_studios_studio_id
  ON anime_studios (studio_id);

-- 부모: users. 회원 삭제 시 댓글 정리.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_comments_user_id
  ON comments (user_id);

-- 부모: users. 신고 3종 모두 유니크가 (대상_id, user_id) 라 user_id 가 선두가 아니다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_comment_reports_user_id
  ON comment_reports (user_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_episode_comment_reports_user_id
  ON episode_comment_reports (user_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_review_reports_user_id
  ON review_reports (user_id);

-- 부모: users. 결제수단 목록 조회가 findByUser_Id... 로 매번 user_id 로 필터한다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_id
  ON payment_methods (user_id);

-- 부모: plans. 플랜 변경/삭제 시 참조 확인.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_plan_id
  ON payments (plan_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_subscriptions_plan_id
  ON subscriptions (plan_id);

-- 부모: users. 스킵 사용 이력.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_skip_usage_user_id
  ON skip_usage (user_id);

ANALYZE ani_favorites;
ANALYZE anime_studios;
ANALYZE comments;
ANALYZE comment_reports;
ANALYZE episode_comment_reports;
ANALYZE review_reports;
ANALYZE payment_methods;
ANALYZE payments;
ANALYZE subscriptions;
ANALYZE skip_usage;
