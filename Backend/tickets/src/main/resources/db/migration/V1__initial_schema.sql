-- V1__initial_schema.sql
-- Initial schema baseline.
-- baseline-on-migrate=true means Flyway marks this as already applied on
-- an existing database without running it again.
-- On a fresh database, Flyway runs this to create everything from scratch.

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    approval_status VARCHAR(255) DEFAULT 'APPROVED',
    approved_at TIMESTAMP,
    rejected_at TIMESTAMP,
    approved_by UUID REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    venue VARCHAR(500) NOT NULL,
    event_start TIMESTAMP,
    event_end TIMESTAMP,
    sales_start TIMESTAMP,
    sales_end TIMESTAMP,
    status VARCHAR(255) NOT NULL,
    max_capacity INTEGER,
    organizer_id UUID REFERENCES users(id),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(19,2) NOT NULL,
    total_available INTEGER,
    event_id UUID REFERENCES events(id),
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(255) NOT NULL,
    original_price NUMERIC(19,2),
    price_paid NUMERIC(19,2),
    discount_applied NUMERIC(19,2),
    purchaser_id UUID REFERENCES users(id),
    ticket_type_id UUID REFERENCES ticket_types(id),
    event_id UUID REFERENCES events(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS discounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discount_type VARCHAR(255) NOT NULL,
    value NUMERIC(19,2) NOT NULL,
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    description VARCHAR(1000),
    ticket_type_id UUID REFERENCES ticket_types(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS qr_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tickets(id),
    qr_code_data TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_validations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES tickets(id),
    validated_by UUID REFERENCES users(id),
    method VARCHAR(255),
    status VARCHAR(255),
    validated_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invite_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(255) NOT NULL UNIQUE,
    role_name VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP,
    created_by UUID REFERENCES users(id),
    redeemed_by UUID REFERENCES users(id),
    redeemed_at TIMESTAMP,
    event_id UUID REFERENCES events(id),
    revoke_reason VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action VARCHAR(255) NOT NULL,
    actor_id UUID REFERENCES users(id),
    target_user_id UUID REFERENCES users(id),
    event_id UUID REFERENCES events(id),
    resource_type VARCHAR(255),
    details TEXT,
    ip_address VARCHAR(255),
    user_agent VARCHAR(1000),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_attending_events (
    user_id UUID REFERENCES users(id),
    event_id UUID REFERENCES events(id),
    PRIMARY KEY (user_id, event_id)
);

CREATE TABLE IF NOT EXISTS user_staffing_events (
    user_id UUID REFERENCES users(id),
    event_id UUID REFERENCES events(id),
    PRIMARY KEY (user_id, event_id)
);

