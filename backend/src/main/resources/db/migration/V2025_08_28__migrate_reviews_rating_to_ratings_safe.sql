-- Safe migration: move reviews.rating into ratings with guards
DO $$
BEGIN
    -- reviews table exists?
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'reviews'
    ) THEN
        -- rating column exists?
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'reviews' AND column_name = 'rating'
        ) THEN
            -- ratings table exists?
            IF EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'ratings'
            ) THEN
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
            END IF;
        END IF;
    END IF;
END $$;


