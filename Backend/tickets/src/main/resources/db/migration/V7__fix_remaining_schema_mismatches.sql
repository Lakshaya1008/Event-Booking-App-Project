-- V7__fix_remaining_schema_mismatches.sql
-- Fixes 7 column mismatches between JPA entity @Column annotations and the
-- actual DB columns created by V1__initial_schema.sql.
-- With spring.jpa.hibernate.ddl-auto=validate, every mismatch causes a
-- SchemaManagementException on startup — application exits immediately .
-- All changes are idempotent: DO $$ ... END$$ blocks check before acting.
-- Safe to run on both fresh databases and existing ones.

-- ============================================================
-- FIX S-2: ticket_validations.validated_by  →  validated_by_id
-- ============================================================
-- TicketValidation entity: @JoinColumn(name = "validated_by_id")
-- V1 schema created:       validated_by UUID REFERENCES users(id)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'validated_by'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'validated_by_id'
    ) THEN
        ALTER TABLE ticket_validations RENAME COLUMN validated_by TO validated_by_id;
    END IF;
END$$;

-- ============================================================
-- FIX S-3: ticket_validations.method  →  validation_method
-- ============================================================
-- TicketValidation entity: @Column(name = "validation_method")
-- V1 schema created:       method VARCHAR(255)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'method'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ticket_validations' AND column_name = 'validation_method'
    ) THEN
        ALTER TABLE ticket_validations RENAME COLUMN method TO validation_method;
    END IF;
END$$;

-- ============================================================
-- FIX S-4: invite_codes.revoke_reason  →  revoked_reason
-- ============================================================
-- InviteCode entity: @Column(name = "revoked_reason")
-- V1 schema created: revoke_reason VARCHAR(500)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'invite_codes' AND column_name = 'revoke_reason'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'invite_codes' AND column_name = 'revoked_reason'
    ) THEN
        ALTER TABLE invite_codes RENAME COLUMN revoke_reason TO revoked_reason;
    END IF;
END$$;

-- ============================================================
-- FIX S-5: invite_codes missing version column
-- ============================================================
-- InviteCode entity: @Version @Column(name = "version") Long version
-- V1 schema:         no version column on invite_codes
ALTER TABLE invite_codes
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- ============================================================
-- FIX S-6: invite_codes missing revoked_at column
-- ============================================================
-- InviteCode entity: @Column(name = "revoked_at") LocalDateTime revokedAt
-- V1 schema:         no revoked_at column
ALTER TABLE invite_codes
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP;

-- ============================================================
-- FIX S-7: qr_codes missing status column
-- ============================================================
-- QrCode entity: @Enumerated(STRING) @Column(name = "status") QrCodeStatusEnum status
-- V1 schema:     no status column on qr_codes (only had 'active BOOLEAN')
ALTER TABLE qr_codes
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

-- ============================================================
-- Verification queries (informational — do not remove)
-- ============================================================
-- After running, confirm columns exist:
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket_validations' ORDER BY ordinal_position;
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'invite_codes'        ORDER BY ordinal_position;
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'qr_codes'            ORDER BY ordinal_position;
