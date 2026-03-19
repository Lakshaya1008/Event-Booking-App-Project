-- V3__add_keycloak_sync_pending.sql
-- Adds keycloak_sync_pending flag to users table.
-- When true, a @Scheduled job retries the Keycloak activation/disable call
-- that failed during approval/rejection (e.g. Keycloak was temporarily down).
-- This prevents the DB and Keycloak falling out of sync silently.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS keycloak_sync_pending BOOLEAN NOT NULL DEFAULT FALSE;

