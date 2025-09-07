-- Add malId column to anime table
-- This allows storing MyAnimeList ID for Jikan API integration

-- Add malId column
ALTER TABLE anime ADD COLUMN mal_id BIGINT;

-- Add unique constraint for mal_id
ALTER TABLE anime ADD CONSTRAINT uk_anime_mal_id UNIQUE (mal_id);

-- Add comment for documentation
COMMENT ON COLUMN anime.mal_id IS 'MyAnimeList ID (Jikan API 식별자)';
