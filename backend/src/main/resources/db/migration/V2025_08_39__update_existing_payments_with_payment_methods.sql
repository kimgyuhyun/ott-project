-- 기존 결제 데이터에 대한 결제수단 정보 업데이트
-- IMPORT 결제 제공자를 사용한 결제들에 대해 기본 결제수단 생성

-- 0. 안전성 검증: enum 타입이 존재하는지 확인
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_method_type') THEN
        RAISE EXCEPTION 'payment_method_type enum이 존재하지 않습니다. V2025_08_38.1을 먼저 실행하세요.';
    END IF;
END $$;

-- 1. 기존 결제가 있는 사용자들을 위한 기본 결제수단 생성 (중복 방지)
INSERT INTO payment_methods (
    user_id, 
    provider, 
    type, 
    provider_method_id, 
    brand, 
    last4, 
    is_default, 
    priority, 
    label, 
    created_at, 
    updated_at
)
SELECT DISTINCT
    p.user_id,
    p.provider,
    CASE 
        WHEN p.provider = 'IMPORT' THEN 'CARD'::payment_method_type
        WHEN p.provider = 'KAKAO_PAY' THEN 'KAKAO_PAY'::payment_method_type
        WHEN p.provider = 'TOSS_PAY' THEN 'TOSS_PAY'::payment_method_type
        WHEN p.provider = 'NICE_PAY' THEN 'NICE_PAY'::payment_method_type
        ELSE 'CARD'::payment_method_type -- 기본값
    END,
    'legacy_' || p.user_id, -- 사용자별로 하나만 생성 (중복 방지)
    CASE 
        WHEN p.provider = 'IMPORT' THEN '신용카드'
        WHEN p.provider = 'KAKAO_PAY' THEN '카카오페이'
        WHEN p.provider = 'TOSS_PAY' THEN '토스페이'
        WHEN p.provider = 'NICE_PAY' THEN '나이스페이'
        ELSE '결제수단'
    END,
    NULL, -- last4 없음
    true, -- 기본 결제수단
    100, -- 기본 우선순위
    CASE 
        WHEN p.provider = 'IMPORT' THEN '신용카드 결제'
        WHEN p.provider = 'KAKAO_PAY' THEN '카카오페이 결제'
        WHEN p.provider = 'TOSS_PAY' THEN '토스페이 결제'
        WHEN p.provider = 'NICE_PAY' THEN '나이스페이 결제'
        ELSE '기본 결제수단'
    END,
    NOW(), -- 생성 시각
    NOW() -- 수정 시각
FROM payments p
WHERE p.status = 'SUCCEEDED'
  AND NOT EXISTS (
    SELECT 1 FROM payment_methods pm 
    WHERE pm.user_id = p.user_id
  );

-- 2. 기존 결제들을 새로 생성된 결제수단과 연결
UPDATE payments 
SET payment_method_id = (
    SELECT pm.id 
    FROM payment_methods pm 
    WHERE pm.user_id = payments.user_id 
      AND pm.provider_method_id = 'legacy_' || payments.user_id
    LIMIT 1
)
WHERE status = 'SUCCEEDED'
  AND payment_method_id IS NULL;

-- 3. 검증: 모든 SUCCEEDED 결제가 결제수단과 연결되었는지 확인
DO $$ 
DECLARE
    unlinked_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO unlinked_count
    FROM payments p
    WHERE p.status = 'SUCCEEDED'
      AND p.payment_method_id IS NULL;
    
    IF unlinked_count > 0 THEN
        RAISE EXCEPTION '마이그레이션 실패: %개의 결제가 결제수단과 연결되지 않았습니다.', unlinked_count;
    END IF;
    
    RAISE NOTICE '마이그레이션 성공: 모든 결제가 결제수단과 연결되었습니다.';
END $$;
