-- Migrate existing review ratings to ratings table and drop reviews.rating
-- Safe for PostgreSQL - handles existing data conflicts

-- 1) Insert missing ratings from reviews into ratings (ON CONFLICT DO NOTHING)
INSERT INTO ratings (user_id, ani_id, score, created_at, updated_at)
SELECT r.user_id, r.ani_id, r.rating,
       COALESCE(r.created_at, NOW()),
       COALESCE(r.updated_at, NOW())
FROM reviews r
WHERE r.rating IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM ratings rt
    WHERE rt.user_id = r.user_id AND rt.ani_id = r.ani_id
  )
ON CONFLICT (user_id, ani_id) DO NOTHING;

-- 2) Drop column from reviews (only if it exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT FROM information_schema.columns 
        WHERE table_name = 'reviews' AND column_name = 'rating'
    ) THEN
        ALTER TABLE reviews DROP COLUMN rating;
    END IF;
END $$;