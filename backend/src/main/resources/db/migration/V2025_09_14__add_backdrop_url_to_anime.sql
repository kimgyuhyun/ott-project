-- Add backdrop_url column to anime table
-- This column will store background images from TMDB API

-- Add backdrop_url column to anime table
ALTER TABLE anime ADD COLUMN backdrop_url VARCHAR(255);

-- Add comment for documentation
COMMENT ON COLUMN anime.backdrop_url IS '배경 이미지 URL (TMDB API에서 제공)';
