-- V5__fix_qr_code_value_column.sql
--
-- FIX-QR1 (BUG 5-3): Change qr_codes.qr_value from VARCHAR(1000) to TEXT.
--
-- ROOT CAUSE:
-- QrCodeServiceImpl stores a base64-encoded PNG (~11,000-20,000 chars) in qr_value.
-- The VARCHAR(1000) column silently truncated every stored image.
-- When the truncated base64 was decoded for download, Base64.getDecoder().decode()
-- threw IllegalArgumentException -- caught and re-thrown as QrCodeNotFoundException.
-- Every QR code download silently failed.
--
-- FIX STEPS:
-- 1. Rename qr_code_data to qr_value if necessary.
-- 2. Alter the column type to TEXT (unbounded in PostgreSQL).
-- 3. Mark all existing QrCode rows as EXPIRED if the status column exists.
--
-- ROLLBACK:
-- Reverting to VARCHAR(1000) would re-introduce the truncation bug.
-- There is no safe rollback -- keep TEXT.

DO $$
BEGIN
    -- Rename column if it exists as qr_code_data
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='qr_codes' AND column_name='qr_code_data') THEN
        ALTER TABLE qr_codes RENAME COLUMN qr_code_data TO qr_value;
    END IF;

    -- Alter column type to TEXT
    ALTER TABLE qr_codes ALTER COLUMN qr_value TYPE TEXT;

    -- Mark existing rows as EXPIRED if status column exists
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='qr_codes' AND column_name='status') THEN
        UPDATE qr_codes SET status = 'EXPIRED' WHERE status = 'ACTIVE';
    END IF;
END $$;

COMMENT ON COLUMN qr_codes.qr_value IS
    'Base64-encoded PNG of the QR code image. '
    'Stores ~11,000-20,000 characters for a 300x300 PNG. '
    'Changed from VARCHAR(1000) to TEXT in V5 migration.';