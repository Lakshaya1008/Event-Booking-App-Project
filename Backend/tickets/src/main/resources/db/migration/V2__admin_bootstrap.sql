-- V2__admin_bootstrap.sql
-- Bootstrap SYSTEM user (audit logging) and admin DB record.

-- SYSTEM user - all-zeros UUID, used as audit actor when no real user context exists.
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

-- ─────────────────────────────────────────────────────────────────────────────
-- ADMIN USER BOOTSTRAP
--
-- FIX: Removed hardcoded Keycloak UUID.
--
-- PROBLEM WITH THE OLD APPROACH:
-- The previous version had a hardcoded UUID ('7612a810-c65c-4047-96e3-3e1c1b425e87').
-- That UUID was the Keycloak-generated ID for admin@test.com in ONE developer's local env.
-- On any other machine (colleague, CI, staging, production), Keycloak generates a
-- completely different UUID for the same user. The result:
--   - Admin record inserted with wrong UUID
--   - Admin JWT carries the real Keycloak UUID
--   - ApprovalGateFilter does findById(jwt.subject) → not found → blocks admin
--   - The admin CANNOT LOG IN on any fresh deployment
--
-- CORRECT APPROACH:
-- The admin user record must be inserted AFTER the Keycloak user is created, using
-- the UUID that Keycloak actually assigned. This migration cannot know that UUID
-- at authoring time. Use the setup script below instead.
--
-- SETUP STEPS FOR A NEW ENVIRONMENT:
-- 1. Create admin@<yourdomain.com> in Keycloak Admin UI manually
--    (Realm → Users → Add user → set email, enable account, set password)
-- 2. Copy the User ID from Keycloak (Users → admin → ID field, looks like a UUID)
-- 3. Run this one-time SQL against your database, replacing <KEYCLOAK_UUID_HERE>:
--
--    INSERT INTO users (id, email, name, approval_status, created_at, updated_at)
--    VALUES (
--        '<KEYCLOAK_UUID_HERE>',
--        'admin@<yourdomain.com>',
--        'Admin User',
--        'APPROVED',
--        now(),
--        now()
--    )
--    ON CONFLICT (id) DO NOTHING;
--
-- 4. Assign the ADMIN role to the user in Keycloak
--    (Users → admin → Role Mappings → Assign ADMIN realm role)
--
-- ALTERNATIVE — environment-variable-driven bootstrap:
-- If your deployment pipeline supports it, inject the admin UUID as an env var
-- and run the INSERT via a startup script (e.g. Flyway placeholder substitution):
--
--    keycloak.admin-user-id=${ADMIN_KEYCLOAK_UUID}
--
-- This migration intentionally does NOT insert an admin record. DatabaseInitializer
-- and the manual setup above handle it. The Flyway migration only bootstraps the
-- SYSTEM user, which has a well-known fixed UUID.
-- ─────────────────────────────────────────────────────────────────────────────