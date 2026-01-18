-- ============================================================================
-- FIX APPROVAL STATUS SCHEMA MIGRATION
-- ============================================================================
-- This script safely adds approval-related columns to existing users table
-- Run this BEFORE starting the application
-- ============================================================================

-- Step 1: Add columns as NULLABLE first (to allow existing rows)
ALTER TABLE users
ADD COLUMN IF NOT EXISTS approval_status VARCHAR(255);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS approved_by UUID;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Step 2: Set default values for existing users (auto-approve existing users)
UPDATE users
SET approval_status = 'APPROVED',
    approved_at = CURRENT_TIMESTAMP,
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP)
WHERE approval_status IS NULL;

-- Step 3: Now make approval_status NOT NULL (safe because all rows have values)
ALTER TABLE users
ALTER COLUMN approval_status SET NOT NULL;

-- Step 4: Add CHECK constraint for approval_status enum
ALTER TABLE users
DROP CONSTRAINT IF EXISTS users_approval_status_check;

ALTER TABLE users
ADD CONSTRAINT users_approval_status_check
CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- Step 5: Add foreign key for approved_by (self-referencing)
ALTER TABLE users
DROP CONSTRAINT IF EXISTS fk_approved_by;

ALTER TABLE users
ADD CONSTRAINT fk_approved_by
FOREIGN KEY (approved_by) REFERENCES users(id);

-- Step 6: Create index for performance
CREATE INDEX IF NOT EXISTS idx_users_approval_status ON users(approval_status);
CREATE INDEX IF NOT EXISTS idx_users_approved_by ON users(approved_by);

-- Verify the changes
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'users'
ORDER BY ordinal_position;

-- Show count of users by approval status
SELECT
    approval_status,
    COUNT(*) as user_count
FROM users
GROUP BY approval_status;

COMMIT;
