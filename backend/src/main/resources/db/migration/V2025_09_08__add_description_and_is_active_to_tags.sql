-- Add is_active field to tags table
-- This migration adds missing field to match the Tag entity

-- Add is_active column with default value
ALTER TABLE tags ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

-- Update existing tags to have is_active = true
UPDATE tags SET is_active = true WHERE is_active IS NULL;
