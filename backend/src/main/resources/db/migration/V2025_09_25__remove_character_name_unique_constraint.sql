-- Character 테이블의 name 컬럼 UNIQUE 제약조건 제거
-- 같은 이름의 캐릭터가 여러 애니메이션에 나올 수 있으므로 UNIQUE 제약조건 제거

-- characters 테이블의 name 컬럼 UNIQUE 제약조건 제거
ALTER TABLE characters DROP CONSTRAINT IF EXISTS characters_name_key;
