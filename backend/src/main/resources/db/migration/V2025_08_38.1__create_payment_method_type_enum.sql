-- payment_method_type enum 타입 생성 (기존 타입이 없을 때만)
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_method_type') THEN
        CREATE TYPE payment_method_type AS ENUM ('CARD', 'KAKAO_PAY', 'TOSS_PAY', 'NICE_PAY', 'BANK_TRANSFER');
    END IF;
END $$;

-- payment_provider enum 타입 생성 (이미 존재하지 않는 경우)
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_provider') THEN
        CREATE TYPE payment_provider AS ENUM ('STRIPE', 'IMPORT', 'TOSS', 'KAKAO');
    END IF;
END $$;

-- 기존 enum 타입이 있다면 안전하게 추가 (하위 호환성)
DO $$ 
BEGIN
    -- payment_method_type에 새로운 값들 추가 (이미 존재하면 무시)
    BEGIN
        ALTER TYPE payment_method_type ADD VALUE IF NOT EXISTS 'KAKAO_PAY';
    EXCEPTION WHEN duplicate_object THEN
        -- 이미 존재하면 무시
    END;
    
    BEGIN
        ALTER TYPE payment_method_type ADD VALUE IF NOT EXISTS 'TOSS_PAY';
    EXCEPTION WHEN duplicate_object THEN
        -- 이미 존재하면 무시
    END;
    
    BEGIN
        ALTER TYPE payment_method_type ADD VALUE IF NOT EXISTS 'NICE_PAY';
    EXCEPTION WHEN duplicate_object THEN
        -- 이미 존재하면 무시
    END;
END $$;
