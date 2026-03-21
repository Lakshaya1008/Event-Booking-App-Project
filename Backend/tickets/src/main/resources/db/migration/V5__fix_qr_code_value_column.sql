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
-- 1. Alter the column type to TEXT (unbounded in PostgreSQL).
-- 2. Mark all existing QrCode rows as EXPIRED -- their stored values are truncated
--    and therefore invalid base64. They will be regenerated on next access.
--    (QrCodeServiceImpl.getActiveQrCodeForTicket() will find no ACTIVE code and
--    throw QrCodeNotFoundException -- the caller should then re-generate via
--    POST /tickets/{id}/qr-codes which calls generateQrCode() again.)
-- 3. Optionally: a backfill job can regenerate all expired QR codes automatically.
--    That is out of scope for this migration -- operational decision.
--
-- ROLLBACK:
-- Reverting to VARCHAR(1000) would re-introduce the truncation bug.
-- There is no safe rollback -- keep TEXT.

ALTER TABLE qr_codes ALTER COLUMN qr_value TYPE TEXT;

-- Mark existing rows as EXPIRED -- their stored base64 is truncated and invalid.
-- New purchases will generate fresh QrCode records with correct values.
UPDATE qr_codes SET status = 'EXPIRED' WHERE status = 'ACTIVE';

COMMENT ON COLUMN qr_codes.qr_value IS
    'Base64-encoded PNG of the QR code image. '
    'Stores ~11,000-20,000 characters for a 300x300 PNG. '
    'Changed from VARCHAR(1000) to TEXT in V5 migration.';