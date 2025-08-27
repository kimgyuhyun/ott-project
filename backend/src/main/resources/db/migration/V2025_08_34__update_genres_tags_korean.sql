-- 장르/태그 한글화 및 일관성 정비(프론트 필터와 매핑)
-- 안전하게 존재하는 항목만 업데이트. 중복 대비해 unique 제약을 가정.

-- 장르 영문 → 한글로 업데이트 (존재 시만)
UPDATE genres SET name = '액션' WHERE name = 'Action';
UPDATE genres SET name = '판타지' WHERE name = 'Fantasy';
UPDATE genres SET name = '코미디' WHERE name = 'Comedy';

-- 태그 영문 → 한글/일관화 (예시)
UPDATE tags SET name = '인기' WHERE name = 'HOT';
UPDATE tags SET name = '트렌딩' WHERE name = 'TRENDING';
UPDATE tags SET name = '신작' WHERE name = 'NEW';

-- 이미 매핑된 작품의 연결은 이름 변경만으로 유지됨(외래키는 id 기반)


