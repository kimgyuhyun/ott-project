-- 리뷰 신고 기록 테이블
-- 목적: "누가 어떤 리뷰를 신고했는지"를 1건씩 저장하여 사용자당 1회로 제한하고,
--       신고 수가 임계치를 넘을 때만 리뷰를 숨기도록(REPORTED) 한다.
--       (기존: 단일 신고 호출로 즉시 숨김 → 악용 가능)

CREATE TABLE review_reports (
    id         BIGSERIAL PRIMARY KEY,
    review_id  BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_reports UNIQUE (review_id, user_id),
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE
);
CREATE INDEX idx_review_reports_review_id ON review_reports (review_id);
