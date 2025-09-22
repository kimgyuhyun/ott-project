-- Add MAL ID columns to master tables for reliable matching
ALTER TABLE characters    ADD COLUMN IF NOT EXISTS mal_id BIGINT;
ALTER TABLE voice_actors  ADD COLUMN IF NOT EXISTS mal_id BIGINT;
ALTER TABLE directors     ADD COLUMN IF NOT EXISTS mal_id BIGINT;

-- Create partial unique indexes to avoid NULL duplicates
CREATE UNIQUE INDEX IF NOT EXISTS uq_characters_mal_id   ON characters(mal_id)   WHERE mal_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_voice_actors_mal_id ON voice_actors(mal_id) WHERE mal_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_directors_mal_id    ON directors(mal_id)    WHERE mal_id IS NOT NULL;

-- Comments (columns exist due to IF NOT EXISTS above)
COMMENT ON COLUMN characters.mal_id   IS 'MyAnimeList Character ID';
COMMENT ON COLUMN voice_actors.mal_id IS 'MyAnimeList Person ID (voice actor)';
COMMENT ON COLUMN directors.mal_id    IS 'MyAnimeList Person ID (director)';


