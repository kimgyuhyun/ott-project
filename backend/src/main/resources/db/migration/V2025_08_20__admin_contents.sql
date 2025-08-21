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

CREATE INDEX idx_admin_contents_type_locale ON admin_contents(type, locale);
CREATE INDEX idx_admin_contents_pub_pos ON admin_contents(published, position);


