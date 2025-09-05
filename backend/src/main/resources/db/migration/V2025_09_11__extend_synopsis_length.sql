-- synopsis 필드 길이 확장 (500자 → 2000자)
-- Jikan API에서 오는 synopsis가 500자를 초과하는 경우가 있어서 확장

ALTER TABLE anime ALTER COLUMN synopsis TYPE VARCHAR(2000);
