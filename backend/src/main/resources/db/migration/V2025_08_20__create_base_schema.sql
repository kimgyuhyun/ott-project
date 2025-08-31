-- Create base schema for OTT project
-- Based on JPA entities: User, Genre, Tag, Studio, Anime, Episode, etc.

-- 1. Create Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create Genres table  
CREATE TABLE genres (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    color VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create Tags table
CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    color VARCHAR(255)
);

-- 4. Create Studios table
CREATE TABLE studios (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    name_en VARCHAR(255),
    name_jp VARCHAR(255),
    description VARCHAR(1000),
    logo_url VARCHAR(255),
    website_url VARCHAR(255),
    country VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Create Anime table
CREATE TABLE anime (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL UNIQUE,
    title_en VARCHAR(255),
    title_jp VARCHAR(255),
    synopsis VARCHAR(500),
    full_synopsis TEXT,
    poster_url VARCHAR(255) NOT NULL,
    total_episodes INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    release_date DATE NOT NULL,
    end_date DATE,
    age_rating VARCHAR(255) NOT NULL,
    rating DOUBLE PRECISION NOT NULL,
    rating_count INTEGER NOT NULL,
    is_exclusive BOOLEAN NOT NULL,
    is_new BOOLEAN NOT NULL,
    is_popular BOOLEAN NOT NULL,
    is_completed BOOLEAN NOT NULL,
    is_subtitle BOOLEAN NOT NULL,
    is_dub BOOLEAN NOT NULL,
    is_simulcast BOOLEAN NOT NULL,
    broadcast_day VARCHAR(255) NOT NULL,
    broad_cast_time VARCHAR(255) NOT NULL,
    season VARCHAR(255) NOT NULL,
    year INTEGER NOT NULL,
    type VARCHAR(255) NOT NULL,
    duration INTEGER NOT NULL,
    source VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    language VARCHAR(255) NOT NULL,
    voice_actors TEXT,
    director VARCHAR(255),
    release_quarter VARCHAR(255),
    current_episodes INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Create Episodes table
CREATE TABLE episodes (
    id BIGSERIAL PRIMARY KEY,
    episode_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    thumbnail_url VARCHAR(255) NOT NULL,
    video_url VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_released BOOLEAN NOT NULL DEFAULT true,
    anime_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE,
    UNIQUE(anime_id, episode_number)
);

-- 7. Create many-to-many relationship tables
CREATE TABLE anime_genres (
    anime_id BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,
    PRIMARY KEY (anime_id, genre_id),
    FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE CASCADE
);

CREATE TABLE anime_tags (
    anime_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (anime_id, tag_id),
    FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE TABLE anime_studios (
    anime_id BIGINT NOT NULL,
    studio_id BIGINT NOT NULL,
    PRIMARY KEY (anime_id, studio_id),
    FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE,
    FOREIGN KEY (studio_id) REFERENCES studios(id) ON DELETE CASCADE
);

-- 8. Create Membership Plans table
CREATE TABLE plans (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    max_quality VARCHAR(255) NOT NULL,
    price_monthly_vat_included BIGINT NOT NULL,
    price_currency VARCHAR(3) NOT NULL,
    period_months INTEGER NOT NULL,
    concurrent_streams INTEGER NOT NULL
);

-- 9. Create Admin Contents table (original functionality)
CREATE TABLE admin_contents (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    locale VARCHAR(8) NOT NULL,
    position INT NOT NULL,
    published BOOLEAN NOT NULL,
    title VARCHAR(128) NOT NULL,
    content TEXT,
    action_text VARCHAR(128),
    action_url VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. Create Payments table
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_session_id VARCHAR(255),
    provider_payment_id VARCHAR(255),
    receipt_url VARCHAR(2048),
    paid_at TIMESTAMP,
    failed_at TIMESTAMP,
    canceled_at TIMESTAMP,
    refunded_amount BIGINT,
    refunded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT
);

-- 11. Create Payment Methods table
CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    provider_method_id VARCHAR(255) NOT NULL,
    brand VARCHAR(50),
    last4 VARCHAR(4),
    expiry_month INTEGER,
    expiry_year INTEGER,
    is_default BOOLEAN NOT NULL DEFAULT false,
    priority INTEGER NOT NULL DEFAULT 100,
    label VARCHAR(100),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 12. Create Subscriptions table
CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP,
    auto_renew BOOLEAN NOT NULL,
    cancel_at_period_end BOOLEAN NOT NULL,
    canceled_at TIMESTAMP,
    next_billing_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry INTEGER NOT NULL DEFAULT 3,
    last_retry_at TIMESTAMP,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(500),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT
);

-- 13. Create Reviews table
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    content TEXT,
    rating NUMERIC(2,1),
    status VARCHAR(20),
    user_id BIGINT,
    ani_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (ani_id) REFERENCES anime(id) ON DELETE CASCADE
);

-- 14. Create Comments table
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    content TEXT,
    status VARCHAR(20),
    user_id BIGINT,
    review_id BIGINT,
    parent_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE
);

-- 15. Create other tables (simplified for commonly used entities)

CREATE TABLE ani_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ani_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (ani_id) REFERENCES anime(id) ON DELETE CASCADE,
    UNIQUE(user_id, ani_id)
);

CREATE TABLE episode_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    episode_id BIGINT NOT NULL,
    position_sec INTEGER NOT NULL DEFAULT 0,
    duration_sec INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    UNIQUE(user_id, episode_id)
);

CREATE TABLE user_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    auto_skip_intro BOOLEAN NOT NULL DEFAULT true,
    auto_skip_ending BOOLEAN NOT NULL DEFAULT true,
    default_quality VARCHAR(255) NOT NULL DEFAULT 'auto',
    auto_next_episode BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id)
);

-- Create additional missing tables
CREATE TABLE ratings (
    id BIGSERIAL PRIMARY KEY,
    score DOUBLE PRECISION NOT NULL,
    user_id BIGINT NOT NULL,
    ani_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (ani_id) REFERENCES anime(id) ON DELETE CASCADE,
    UNIQUE(user_id, ani_id)
);

CREATE TABLE review_likes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    UNIQUE(user_id, review_id)
);

CREATE TABLE comment_likes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    UNIQUE(user_id, comment_id)
);

CREATE TABLE user_social_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(provider, provider_id)
);

CREATE TABLE episode_skip_meta (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    intro_start INTEGER,
    intro_end INTEGER,
    outro_start INTEGER,
    outro_end INTEGER,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    UNIQUE(episode_id)
);

-- Need additional tables for full entity support
CREATE TABLE skip_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    episode_id BIGINT NOT NULL,
    skip_type VARCHAR(20) NOT NULL,
    position_sec INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE
);

CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    key_value VARCHAR(191) NOT NULL UNIQUE,
    purpose VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_anime_status ON anime(status);
CREATE INDEX idx_anime_release_date ON anime(release_date);
CREATE INDEX idx_anime_rating ON anime(rating);
CREATE INDEX idx_episodes_anime_id ON episodes(anime_id);
CREATE INDEX idx_episodes_number ON episodes(anime_id, episode_number);
CREATE INDEX idx_admin_contents_type_locale ON admin_contents(type, locale);
CREATE INDEX idx_admin_contents_pub_pos ON admin_contents(published, position);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_user_id_status ON payments(user_id, status);
CREATE INDEX idx_payments_merchant_uid ON payments(provider_session_id);
CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE INDEX idx_subscriptions_user_id_status ON subscriptions(user_id, status);
CREATE INDEX idx_reviews_anime_id ON reviews(ani_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
CREATE INDEX idx_comments_review_id ON comments(review_id);
CREATE INDEX idx_episode_progress_user_id ON episode_progress(user_id);
CREATE INDEX idx_ratings_ani_id ON ratings(ani_id);
CREATE INDEX idx_ratings_user_id ON ratings(user_id);
CREATE INDEX idx_review_likes_review_id ON review_likes(review_id);
CREATE INDEX idx_comment_likes_comment_id ON comment_likes(comment_id);
CREATE INDEX idx_social_accounts_user_id ON user_social_accounts(user_id);
CREATE INDEX idx_skip_usage_episode_id ON skip_usage(episode_id);
CREATE INDEX idx_idempotency_keys_key_value ON idempotency_keys(key_value);


