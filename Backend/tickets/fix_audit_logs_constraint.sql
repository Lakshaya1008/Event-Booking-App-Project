-- ============================================================================
-- REMOVE AUDIT LOGS ACTION CHECK CONSTRAINT
-- ============================================================================
-- This script removes the audit_logs_action_check constraint to allow all
-- AuditAction enum values without database-level restrictions.
-- Validation is handled at the Java enum level only.
-- This migration is safe and idempotent.
-- ============================================================================

ALTER TABLE audit_logs
DROP CONSTRAINT IF EXISTS audit_logs_action_check;
