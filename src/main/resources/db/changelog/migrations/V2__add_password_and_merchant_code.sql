-- ============================================================
-- V2: Add password and merchant_code to merchants
-- ============================================================

ALTER TABLE merchants
    ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT 'CHANGE_ME';

ALTER TABLE merchants
    ADD COLUMN merchant_code VARCHAR(20) NOT NULL;

-- Backfill existing rows with a temporary unique code (won't matter in dev, fresh DB)
UPDATE merchants
SET merchant_code = 'MCH-' || UPPER(SUBSTRING(id::text, 1, 6))
WHERE merchant_code IS NULL;

ALTER TABLE merchants
    ALTER COLUMN merchant_code SET NOT NULL;

ALTER TABLE merchants
    ADD CONSTRAINT uq_merchants_merchant_code UNIQUE (merchant_code);

CREATE INDEX idx_merchants_merchant_code ON merchants(merchant_code);