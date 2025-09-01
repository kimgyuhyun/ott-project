-- PaymentMethodType enum에 누락된 값들 추가
-- 엔티티에는 정의되어 있지만 데이터베이스 enum에는 없는 값들을 추가

-- 1. 현재 enum 값들 확인
DO $$ 
BEGIN
    RAISE NOTICE '현재 payment_method_type enum 값들:';
    RAISE NOTICE '%', (SELECT string_agg(enumlabel, ', ') FROM pg_enum e JOIN pg_type t ON e.enumtypid = t.oid WHERE t.typname = 'payment_method_type');
END $$;

-- 2. 누락된 enum 값들 추가
ALTER TYPE payment_method_type ADD VALUE IF NOT EXISTS 'KAKAO_PAY';
ALTER TYPE payment_method_type ADD VALUE IF NOT EXISTS 'TOSS_PAY';
ALTER TYPE payment_method_type ADD VALUE IF NOT EXISTS 'NICE_PAY';

-- 3. 추가 후 enum 값들 확인
DO $$ 
BEGIN
    RAISE NOTICE '추가 후 payment_method_type enum 값들:';
    RAISE NOTICE '%', (SELECT string_agg(enumlabel, ', ') FROM pg_enum e JOIN pg_type t ON e.enumtypid = t.oid WHERE t.typname = 'payment_method_type');
END $$;

-- 4. 기존 ACCOUNT 타입을 provider에 따라 적절한 타입으로 수정
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
WHERE type::text = 'ACCOUNT';

-- 5. 수정 결과 확인
DO $$ 
BEGIN
    RAISE NOTICE '수정 후 payment_methods 타입 분포:';
    RAISE NOTICE 'CARD 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'CARD'::payment_method_type);
    RAISE NOTICE 'KAKAO_PAY 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'KAKAO_PAY'::payment_method_type);
    RAISE NOTICE 'TOSS_PAY 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'TOSS_PAY'::payment_method_type);
    RAISE NOTICE 'NICE_PAY 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'NICE_PAY'::payment_method_type);
    RAISE NOTICE 'BANK_TRANSFER 타입: %개', (SELECT COUNT(*) FROM payment_methods WHERE type = 'BANK_TRANSFER'::payment_method_type);
END $$;
