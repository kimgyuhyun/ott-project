-- Remove unique constraint from voice_actors.name column
-- This allows duplicate voice actor names to be stored

-- Drop the unique constraint on name column
ALTER TABLE voice_actors DROP CONSTRAINT IF EXISTS uk_voice_actors_name;

-- Add comment for documentation
COMMENT ON COLUMN voice_actors.name IS '성우 이름 (한글) - 중복 허용';
