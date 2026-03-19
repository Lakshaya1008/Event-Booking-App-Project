-- V2__admin_bootstrap.sql
-- Bootstrap SYSTEM user (audit logging) and admin@test.com DB record.
-- Replaces the manually-run fix_admin_bootstrap.sql.

-- SYSTEM user - all-zeros UUID, used as audit actor when no real user context exists
INSERT INTO users (id, email, name, approval_status, created_at, updated_at)
SELECT
    '00000000-0000-0000-0000-000000000000',
    'system@system.local',
    'SYSTEM',
    'APPROVED',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '00000000-0000-0000-0000-000000000000'
);

-- Admin user DB record.
-- IMPORTANT: The UUID below must match the Keycloak user ID for admin@test.com.
-- After creating admin@test.com in Keycloak (step A5 of the testing guide),
-- copy their UUID from Keycloak Admin UI -> Users -> admin@test.com -> ID field.
-- Update the UUID here, then this migration will insert the correct record.
INSERT INTO users (id, email, name, approval_status, created_at, updated_at)
SELECT
    '7612a810-c65c-4047-96e3-3e1c1b425e87',
    'admin@test.com',
    'Admin User',
    'APPROVED',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'admin@test.com'
);

