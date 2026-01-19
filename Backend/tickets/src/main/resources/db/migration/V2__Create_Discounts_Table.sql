-- ============================================================================
-- Flyway Migration V2: Create Discounts Table
-- ============================================================================
-- Purpose: Create discount system to allow organizers to offer percentage-based
--          or fixed-amount discounts on ticket types.
--
-- Business Rules:
--   - Only ONE active discount per ticket type at a time
--   - Discounts apply at purchase time only (never retroactive)
--   - Organizer must own both event and ticket type
--   - Discount type: PERCENTAGE (e.g., 20%) or FIXED_AMOUNT (e.g., $10 off)
--
-- Date: 2026-01-19
-- ============================================================================

-- Create discount_type enum
CREATE TYPE discount_type AS ENUM ('PERCENTAGE', 'FIXED_AMOUNT');

-- Create discounts table
CREATE TABLE discounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign key to ticket_type
    ticket_type_id UUID NOT NULL,

    -- Discount configuration
    discount_type discount_type NOT NULL,
    value NUMERIC(10, 2) NOT NULL CHECK (value > 0),

    -- Validity period
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,

    -- Control flags
    active BOOLEAN NOT NULL DEFAULT true,

    -- Metadata
    description VARCHAR(500),
    created_by UUID, -- Optional: track which organizer created it

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_discount_ticket_type
        FOREIGN KEY (ticket_type_id)
        REFERENCES ticket_types(id)
        ON DELETE CASCADE,

    -- Business rule: valid_to must be after valid_from
    CONSTRAINT check_validity_period
        CHECK (valid_to > valid_from),

    -- Business rule: percentage must be between 0-100
    CONSTRAINT check_percentage_range
        CHECK (
            discount_type != 'PERCENTAGE'
            OR (value > 0 AND value <= 100)
        )
);

-- Create unique partial index: only ONE active discount per ticket type
-- This enforces the business rule at database level
CREATE UNIQUE INDEX idx_discounts_one_active_per_ticket_type
ON discounts (ticket_type_id)
WHERE active = true;

-- Create index for querying active discounts by validity period
CREATE INDEX idx_discounts_active_validity
ON discounts (ticket_type_id, active, valid_from, valid_to)
WHERE active = true;

-- Create index for audit/reporting queries
CREATE INDEX idx_discounts_created_at ON discounts(created_at);
CREATE INDEX idx_discounts_ticket_type ON discounts(ticket_type_id);

-- Add table comment
COMMENT ON TABLE discounts IS 'Stores discount configurations for ticket types. Only one active discount allowed per ticket type at a time.';

-- Add column comments
COMMENT ON COLUMN discounts.discount_type IS 'Type of discount: PERCENTAGE (e.g., 20 means 20% off) or FIXED_AMOUNT (e.g., 10.00 means $10 off)';
COMMENT ON COLUMN discounts.value IS 'Discount value: percentage (0-100) or fixed amount in currency';
COMMENT ON COLUMN discounts.active IS 'Whether discount is currently active. Only one active discount per ticket type allowed.';
COMMENT ON COLUMN discounts.valid_from IS 'Start date/time when discount becomes valid';
COMMENT ON COLUMN discounts.valid_to IS 'End date/time when discount expires';
