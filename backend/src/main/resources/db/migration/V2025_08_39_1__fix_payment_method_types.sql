-- 기존 payment_methods 테이블의 잘못된 타입 수정
-- ACCOUNT 타입을 올바른 payment_method_type으로 변경

-- 1. 기존 잘못된 타입 데이터 확인
DO $$ 
BEGIN
    RAISE NOTICE '수정 전 payment_methods 타입 분포:';
    RAISE NOTICE 'IMPORT provider: %개', (SELECT COUNT(*) FROM payment_methods WHERE provider = 'IMPORT');
    RAISE NOTICE 'KAKAO_PAY provider: %개', (SELECT COUNT(*) FROM payment_methods WHERE provider = 'KAKAO_PAY');
    RAISE NOTICE 'TOSS_PAY provider: %개', (SELECT COUNT(*) FROM payment_methods WHERE provider = 'TOSS_PAY');
    RAISE NOTICE 'NICE_PAY provider: %개', (SELECT COUNT(*) FROM payment_methods WHERE provider = 'NICE_PAY');
END $$;

-- 2. 잘못된 타입을 올바른 타입으로 수정
UPDATE payment_methods 
SET type = CASE 
    WHEN provider = 'IMPORT' THEN 'CARD'::payment_method_type
    WHEN provider = 'KAKAO_PAY' THEN 'KAKAO_PAY'::payment_method_type
    WHEN provider = 'TOSS_PAY' THEN 'TOSS_PAY'::payment_method_type
    WHEN provider = 'NICE_PAY' THEN 'NICE_PAY'::payment_method_type
    ELSE 'CARD'::payment_method_type
END,
    brand = CASE 
    WHEN provider = 'IMPORT' THEN '신용카드'
    WHEN provider = 'KAKAO_PAY' THEN '카카오페이'
    WHEN provider = 'TOSS_PAY' THEN '토스페이'
    WHEN provider = 'NICE_PAY' THEN '나이스페이'
    ELSE '결제수단'
END,
    label = CASE 
    WHEN provider = 'IMPORT' THEN '신용카드 결제'
    WHEN provider = 'KAKAO_PAY' THEN '카카오페이 결제'
    WHEN provider = 'TOSS_PAY' THEN '토스페이 결제'
    WHEN provider = 'NICE_PAY' THEN '나이스페이 결제'
    ELSE '기본 결제수단'
END,
    updated_at = NOW()
WHERE type IS NULL OR type::text = 'ACCOUNT';

-- 3. 수정 후 결과 확인
DO $$ 
BEGIN
    RAISE NOTICE '수정 후 payment_methods 타입 분포:';
    RAISE NOTICE 'CARD 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'CARD'::payment_method_type);
    RAISE NOTICE 'KAKAO_PAY 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'KAKAO_PAY'::payment_method_type);
    RAISE NOTICE 'TOSS_PAY 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'TOSS_PAY'::payment_method_type);
    RAISE NOTICE 'NICE_PAY 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'NICE_PAY'::payment_method_type);
END $$;
