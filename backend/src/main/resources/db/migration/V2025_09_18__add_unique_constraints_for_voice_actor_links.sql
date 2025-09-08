-- Ensure unique link between anime and voice_actor
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_anime_voice_actor'
  ) THEN
    ALTER TABLE anime_voice_actors
      ADD CONSTRAINT uq_anime_voice_actor UNIQUE (anime_id, voice_actor_id);
  END IF;
END $$;

-- Ensure unique link between character and voice_actor (if table exists)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'character_voice_actors'
  ) AND NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uq_character_voice_actor'
  ) THEN
    ALTER TABLE character_voice_actors
      ADD CONSTRAINT uq_character_voice_actor UNIQUE (character_id, voice_actor_id);
  END IF;
END $$;


