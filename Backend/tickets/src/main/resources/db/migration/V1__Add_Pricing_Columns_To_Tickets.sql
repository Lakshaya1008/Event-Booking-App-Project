-- ============================================================================
-- Flyway Migration V1: Add Pricing Columns to Tickets Table
-- ============================================================================
-- Purpose: Add pricing audit columns to track original price, discount applied,
--          and final price paid for each ticket purchase.
--
-- Rationale:
--   - Historical accuracy: Shows exact price paid at purchase time
--   - Audit trail: Tracks discount application
--   - Accounting compliance: Required for financial reporting
--   - Immutability: Past purchases unaffected by future price changes
--
-- Date: 2026-01-19
-- ============================================================================

-- Add new pricing columns (nullable initially for backward compatibility)
ALTER TABLE tickets
ADD COLUMN IF NOT EXISTS original_price NUMERIC(10, 2),
ADD COLUMN IF NOT EXISTS price_paid NUMERIC(10, 2),
ADD COLUMN IF NOT EXISTS discount_applied NUMERIC(10, 2);

-- Backfill existing tickets with their ticket_type prices
-- This assumes ticket prices haven't changed since purchase
UPDATE tickets t
SET
    original_price = tt.price,
    price_paid = tt.price,
    discount_applied = 0
FROM ticket_types tt
WHERE t.ticket_type_id = tt.id
AND t.price_paid IS NULL;

-- Now make price_paid NOT NULL (all tickets have values)
ALTER TABLE tickets
ALTER COLUMN price_paid SET NOT NULL;

-- Add check constraints to ensure data integrity
ALTER TABLE tickets
ADD CONSTRAINT check_price_paid_positive
CHECK (price_paid >= 0);

ALTER TABLE tickets
ADD CONSTRAINT check_original_price_positive
CHECK (original_price >= 0);

ALTER TABLE tickets
ADD CONSTRAINT check_discount_valid
CHECK (discount_applied >= 0 AND discount_applied <= original_price);

-- Add comment for documentation
COMMENT ON COLUMN tickets.original_price IS 'Base price of ticket type at time of purchase (before discount)';
COMMENT ON COLUMN tickets.price_paid IS 'Final price paid by customer (after discount applied)';
COMMENT ON COLUMN tickets.discount_applied IS 'Amount discounted from original price (0 if no discount)';

-- Create index for reporting queries
CREATE INDEX IF NOT EXISTS idx_tickets_price_paid ON tickets(price_paid);
CREATE INDEX IF NOT EXISTS idx_tickets_purchase_date ON tickets(created_at);
