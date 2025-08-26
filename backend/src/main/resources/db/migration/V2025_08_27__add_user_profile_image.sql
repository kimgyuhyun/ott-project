-- Add profile image column to users table (safe/idempotent)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_image VARCHAR(512);


