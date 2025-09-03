-- Create episode comments and episode comment likes tables
-- Based on EpisodeComment and EpisodeCommentLike entities

-- 1. Create episode_comments table
CREATE TABLE episode_comments (
    id BIGSERIAL PRIMARY KEY,
    content TEXT,
    status VARCHAR(20),
    user_id BIGINT,
    episode_id BIGINT NOT NULL,
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES episode_comments(id) ON DELETE CASCADE
);

-- 2. Create episode_comment_likes table
CREATE TABLE episode_comment_likes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    episode_comment_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_comment_id) REFERENCES episode_comments(id) ON DELETE CASCADE,
    UNIQUE(user_id, episode_comment_id)
);

-- 3. Create indexes for better performance
CREATE INDEX idx_episode_comments_episode_id ON episode_comments(episode_id);
CREATE INDEX idx_episode_comments_user_id ON episode_comments(user_id);
CREATE INDEX idx_episode_comments_parent_id ON episode_comments(parent_id);
CREATE INDEX idx_episode_comments_status ON episode_comments(status);
CREATE INDEX idx_episode_comments_created_at ON episode_comments(created_at);

CREATE INDEX idx_episode_comment_likes_user_id ON episode_comment_likes(user_id);
CREATE INDEX idx_episode_comment_likes_episode_comment_id ON episode_comment_likes(episode_comment_id);
