-- Payment 테이블에 메타데이터 필드 추가
ALTER TABLE payments 
ADD COLUMN description VARCHAR(2048),
ADD COLUMN metadata TEXT,
ADD COLUMN completed_at TIMESTAMP;
