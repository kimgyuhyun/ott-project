-- Remove voice_actors_name_key constraint from voice_actors.name column
-- This allows duplicate voice actor names to be stored

-- Drop the specific constraint that was causing the error
ALTER TABLE voice_actors DROP CONSTRAINT IF EXISTS voice_actors_name_key;

-- Add comment for documentation
COMMENT ON COLUMN voice_actors.name IS '성우 이름 (한글) - 중복 허용';
