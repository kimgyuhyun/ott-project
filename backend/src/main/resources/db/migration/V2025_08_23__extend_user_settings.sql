-- Extend user_settings with new preference columns to match JPA entity
-- Safe to run repeatedly using IF NOT EXISTS

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS theme VARCHAR(255) NOT NULL DEFAULT 'light';

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS language VARCHAR(8) NOT NULL DEFAULT 'ko';

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS notifications BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS auto_play BOOLEAN NOT NULL DEFAULT false;


