-- anime 테이블의 title 컬럼을 nullable로 변경
-- 한국어 제목이 없는 애니메이션의 경우 title을 null로 저장할 수 있도록 허용

-- title 컬럼의 NOT NULL 제약조건 제거
ALTER TABLE anime ALTER COLUMN title DROP NOT NULL;

-- 기존 데이터에서 빈 문자열인 title을 null로 변경 (선택사항)
UPDATE anime SET title = NULL WHERE title = '';

-- title 컬럼에 대한 인덱스는 unique 제약조건이 있으므로 그대로 유지
