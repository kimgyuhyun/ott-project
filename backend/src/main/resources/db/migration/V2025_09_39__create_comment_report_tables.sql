-- 댓글 신고 기록 테이블
-- 목적: "누가 어떤 댓글을 신고했는지"를 1건씩 저장하여 사용자당 1회로 제한하고,
--       신고 수가 임계치를 넘을 때만 댓글을 숨기도록(REPORTED) 한다.
--       (기존: 단일 신고 호출로 즉시 숨김 → 악용 가능)

CREATE TABLE comment_reports (
    id         BIGSERIAL PRIMARY KEY,
    comment_id BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_comment_reports UNIQUE (comment_id, user_id),
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
);
CREATE INDEX idx_comment_reports_comment_id ON comment_reports (comment_id);

CREATE TABLE episode_comment_reports (
    id                 BIGSERIAL PRIMARY KEY,
    episode_comment_id BIGINT    NOT NULL,
    user_id            BIGINT    NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_episode_comment_reports UNIQUE (episode_comment_id, user_id),
    FOREIGN KEY (episode_comment_id) REFERENCES episode_comments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id)            REFERENCES users(id)            ON DELETE CASCADE
);
CREATE INDEX idx_episode_comment_reports_comment_id ON episode_comment_reports (episode_comment_id);
