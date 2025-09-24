-- Fix payments table amount/currency columns to match JPA Money VO mapping
-- Remove old amount/currency columns and make price_amount/price_currency NOT NULL

-- Step 1: Ensure all existing data is migrated to new columns
UPDATE payments 
SET price_amount = amount, 
    price_currency = currency 
WHERE price_amount IS NULL OR price_currency IS NULL;

-- Step 2: Add NOT NULL constraints to new columns
ALTER TABLE payments ALTER COLUMN price_amount SET NOT NULL;
ALTER TABLE payments ALTER COLUMN price_currency SET NOT NULL;

-- Step 3: Drop old columns and their constraints
ALTER TABLE payments DROP COLUMN IF EXISTS amount;
ALTER TABLE payments DROP COLUMN IF EXISTS currency;

-- Step 4: Add comments for documentation
COMMENT ON COLUMN payments.price_amount IS 'Payment amount in smallest currency unit (Money VO)';
COMMENT ON COLUMN payments.price_currency IS 'Payment currency code (ISO 4217, Money VO)';
