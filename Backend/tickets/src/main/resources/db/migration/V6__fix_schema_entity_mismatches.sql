-- V6__fix_schema_entity_mismatches.sql
-- Fixes three columns where the JPA entity @Column(name=...) differs from
-- the actual DB column name. With spring.jpa.hibernate.ddl-auto=validate,
-- Hibernate checks the schema on startup and throws SchemaManagementException
-- if any mapped column is missing -- preventing the application from starting.
--
-- All changes are idempotent (IF NOT EXISTS / IF EXISTS guards).
-- Safe to run against both fresh and existing databases.

-- ============================================================
-- FIX 1: discounts.value -> discounts.discount_value
-- ============================================================
-- Discount entity maps: @Column(name = "discount_value")
-- V1 schema created:    value NUMERIC(19,2)
-- Hibernate validate:   FAIL -- column "discount_value" not found
--
-- Rename the existing column to match the entity mapping.
-- Uses DO block to guard against double-execution.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'discounts' AND column_name = 'value'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'discounts' AND column_name = 'discount_value'
    ) THEN
        ALTER TABLE discounts RENAME COLUMN value TO discount_value;
    END IF;
END$$;

-- ============================================================
-- FIX 2: audit_logs missing resource_id column
-- ============================================================
-- AuditLog entity maps: @Column(name = "resource_id") UUID resourceId
-- V1 schema:            resource_type VARCHAR(255) -- but NO resource_id column
-- Hibernate validate:   FAIL -- column "resource_id" not found
--
-- Add the missing column. NULL allowed: not all audit events have a resource.
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS resource_id UUID;

-- ============================================================
-- FIX 3: discounts missing created_by column
-- ============================================================
-- Discount entity maps: @Column(name = "created_by") UUID createdBy
-- V1 schema:            no created_by column on discounts table
-- Hibernate validate:   FAIL -- column "created_by" not found
--
-- Add the missing column. NULL allowed: existing discount rows have no creator recorded.
ALTER TABLE discounts
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);

-- ============================================================
-- Verification queries (informational -- do not remove)
-- ============================================================
-- After running, confirm:
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'discounts' ORDER BY ordinal_position;
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'audit_logs'  ORDER BY ordinal_position;

