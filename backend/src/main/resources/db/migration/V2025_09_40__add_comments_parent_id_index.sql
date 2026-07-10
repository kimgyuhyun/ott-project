-- 리뷰 댓글 조회 성능 인덱스
-- 대댓글 수 서브쿼리 / 대댓글 목록 / 삭제 부모 tombstone(EXISTS)이 모두 comments.parent_id로
-- 필터하는데 인덱스가 없어 매 조회마다 풀스캔이었다. (에피소드 댓글은 이미 parent_id 인덱스 보유)
CREATE INDEX IF NOT EXISTS idx_comments_parent_id ON comments (parent_id);
