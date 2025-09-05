-- Create Director, VoiceActor, Character tables and their relationships
-- This migration adds the new entity tables for proper relational structure

-- 1. Create Directors table
CREATE TABLE directors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    name_en VARCHAR(255),
    name_jp VARCHAR(255),
    profile_url VARCHAR(255),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create VoiceActors table
CREATE TABLE voice_actors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    name_en VARCHAR(255),
    name_jp VARCHAR(255),
    profile_url VARCHAR(255),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create Characters table
CREATE TABLE characters (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    name_en VARCHAR(255),
    name_jp VARCHAR(255),
    image_url VARCHAR(255),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Create many-to-many relationship tables
CREATE TABLE anime_directors (
    anime_id BIGINT NOT NULL,
    director_id BIGINT NOT NULL,
    PRIMARY KEY (anime_id, director_id),
    FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE,
    FOREIGN KEY (director_id) REFERENCES directors(id) ON DELETE CASCADE
);

CREATE TABLE anime_voice_actors (
    anime_id BIGINT NOT NULL,
    voice_actor_id BIGINT NOT NULL,
    PRIMARY KEY (anime_id, voice_actor_id),
    FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE,
    FOREIGN KEY (voice_actor_id) REFERENCES voice_actors(id) ON DELETE CASCADE
);

CREATE TABLE anime_characters (
    anime_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    PRIMARY KEY (anime_id, character_id),
    FOREIGN KEY (anime_id) REFERENCES anime(id) ON DELETE CASCADE,
    FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
);

CREATE TABLE character_voice_actors (
    character_id BIGINT NOT NULL,
    voice_actor_id BIGINT NOT NULL,
    PRIMARY KEY (character_id, voice_actor_id),
    FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE,
    FOREIGN KEY (voice_actor_id) REFERENCES voice_actors(id) ON DELETE CASCADE
);

-- 5. Create indexes for better performance
CREATE INDEX idx_directors_name ON directors(name);
CREATE INDEX idx_voice_actors_name ON voice_actors(name);
CREATE INDEX idx_characters_name ON characters(name);
CREATE INDEX idx_anime_directors_anime_id ON anime_directors(anime_id);
CREATE INDEX idx_anime_directors_director_id ON anime_directors(director_id);
CREATE INDEX idx_anime_voice_actors_anime_id ON anime_voice_actors(anime_id);
CREATE INDEX idx_anime_voice_actors_voice_actor_id ON anime_voice_actors(voice_actor_id);
CREATE INDEX idx_anime_characters_anime_id ON anime_characters(anime_id);
CREATE INDEX idx_anime_characters_character_id ON anime_characters(character_id);
CREATE INDEX idx_character_voice_actors_character_id ON character_voice_actors(character_id);
CREATE INDEX idx_character_voice_actors_voice_actor_id ON character_voice_actors(voice_actor_id);
