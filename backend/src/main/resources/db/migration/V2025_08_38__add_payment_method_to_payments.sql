-- payments 테이블에 payment_method_id 컬럼 추가
ALTER TABLE payments ADD COLUMN payment_method_id BIGINT;

-- payment_method_id에 대한 외래키 제약조건 추가
ALTER TABLE payments 
ADD CONSTRAINT fk_payments_payment_method 
FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id);

-- payment_method_id 컬럼에 대한 인덱스 추가 (성능 향상)
CREATE INDEX idx_payments_payment_method_id ON payments(payment_method_id);
