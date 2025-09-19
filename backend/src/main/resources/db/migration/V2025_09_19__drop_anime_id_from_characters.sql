-- Drop obsolete anime_id column from characters table to rely solely on join table
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'characters'
          AND column_name = 'anime_id'
    ) THEN
        ALTER TABLE characters DROP COLUMN IF EXISTS anime_id;
    END IF;
END $$;


