-- payments 테이블에 Money VO 매핑을 위한 price_amount, price_currency 컬럼 추가
-- 기존 amount, currency 컬럼과 중복이지만 Money VO의 @AttributeOverride 때문

-- price_amount 컬럼 추가 (기존 amount와 동일한 타입)
ALTER TABLE payments ADD COLUMN price_amount BIGINT;

-- price_currency 컬럼 추가 (기존 currency와 동일한 타입)
ALTER TABLE payments ADD COLUMN price_currency VARCHAR(3);

-- 기존 데이터를 새 컬럼으로 복사 (기존 데이터가 있다면)
UPDATE payments 
SET price_amount = amount, 
    price_currency = currency 
WHERE price_amount IS NULL OR price_currency IS NULL;

-- 인덱스 추가 (성능 향상)
CREATE INDEX idx_payments_price_amount ON payments(price_amount);
CREATE INDEX idx_payments_price_currency ON payments(price_currency);
