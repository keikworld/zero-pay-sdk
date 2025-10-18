-- ==============================================================================
-- ZeroPay Database Schema
-- PostgreSQL 14+
-- ==============================================================================
--
-- Security:
-- - Wrapped keys only (never raw digests)
-- - UUID primary keys (no sequential IDs)
-- - Audit trail with timestamps
-- - Index optimization for performance
-- - GDPR compliance (right to erasure)
--
-- Version: 1.0.0
-- Last Updated: 2025-10-18
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- TABLE: wrapped_keys
-- Stores KMS-wrapped encryption keys (NOT the actual factor digests)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS wrapped_keys (
    -- Primary key: User UUID (generated client-side)
    user_uuid UUID PRIMARY KEY,

    -- KMS-wrapped encryption key (binary data)
    -- This key is used to encrypt factor digests in Redis
    wrapped_key BYTEA NOT NULL,

    -- KMS key ID used for wrapping
    kms_key_id VARCHAR(255) NOT NULL,

    -- Encryption context (additional authenticated data)
    encryption_context JSONB,

    -- Key version for rotation support
    key_version INTEGER NOT NULL DEFAULT 1,

    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMP WITH TIME ZONE,

    -- TTL for automatic deletion (GDPR compliance)
    expires_at TIMESTAMP WITH TIME ZONE,

    -- Soft delete flag (for GDPR right to erasure)
    deleted_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Audit trail
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    -- Constraints
    CONSTRAINT wrapped_key_not_empty CHECK (length(wrapped_key) > 0),
    CONSTRAINT key_version_positive CHECK (key_version > 0)
);

-- Indexes for performance
CREATE INDEX idx_wrapped_keys_created_at ON wrapped_keys(created_at);
CREATE INDEX idx_wrapped_keys_expires_at ON wrapped_keys(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_wrapped_keys_deleted ON wrapped_keys(is_deleted, deleted_at) WHERE is_deleted = TRUE;
CREATE INDEX idx_wrapped_keys_key_version ON wrapped_keys(key_version);

-- ==============================================================================
-- TABLE: blockchain_wallets
-- Stores linked blockchain wallet addresses
-- ==============================================================================

CREATE TABLE IF NOT EXISTS blockchain_wallets (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Foreign key to user
    user_uuid UUID NOT NULL REFERENCES wrapped_keys(user_uuid) ON DELETE CASCADE,

    -- Wallet details
    wallet_address VARCHAR(255) NOT NULL,
    blockchain_network VARCHAR(50) NOT NULL, -- 'solana', 'ethereum', etc.
    wallet_type VARCHAR(50) NOT NULL, -- 'phantom', 'metamask', etc.

    -- Verification status
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP WITH TIME ZONE,
    verification_signature TEXT,

    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP WITH TIME ZONE,

    -- Soft delete
    deleted_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Constraints
    CONSTRAINT unique_user_wallet UNIQUE(user_uuid, wallet_address, blockchain_network),
    CONSTRAINT wallet_address_not_empty CHECK (char_length(wallet_address) > 0),
    CONSTRAINT blockchain_network_valid CHECK (blockchain_network IN ('solana', 'ethereum', 'polygon', 'binance')),
    CONSTRAINT wallet_type_valid CHECK (wallet_type IN ('phantom', 'metamask', 'coinbase', 'trust', 'other'))
);

-- Indexes
CREATE INDEX idx_blockchain_wallets_user_uuid ON blockchain_wallets(user_uuid);
CREATE INDEX idx_blockchain_wallets_address ON blockchain_wallets(wallet_address);
CREATE INDEX idx_blockchain_wallets_network ON blockchain_wallets(blockchain_network);
CREATE INDEX idx_blockchain_wallets_verified ON blockchain_wallets(is_verified);

-- ==============================================================================
-- TABLE: audit_log
-- Security audit trail for compliance
-- ==============================================================================

CREATE TABLE IF NOT EXISTS audit_log (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- User context
    user_uuid UUID REFERENCES wrapped_keys(user_uuid) ON DELETE SET NULL,

    -- Event details
    event_type VARCHAR(100) NOT NULL, -- 'enrollment', 'verification', 'key_rotation', 'deletion', etc.
    event_action VARCHAR(50) NOT NULL, -- 'create', 'read', 'update', 'delete'
    event_status VARCHAR(20) NOT NULL, -- 'success', 'failure', 'error'

    -- Request metadata
    ip_address INET,
    user_agent TEXT,
    request_id VARCHAR(255),
    session_id VARCHAR(255),

    -- Event payload (encrypted sensitive data)
    event_data JSONB,

    -- Error details (if any)
    error_code VARCHAR(50),
    error_message TEXT,

    -- Timestamp
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT event_type_not_empty CHECK (char_length(event_type) > 0),
    CONSTRAINT event_status_valid CHECK (event_status IN ('success', 'failure', 'error', 'pending'))
);

-- Indexes for audit queries
CREATE INDEX idx_audit_log_user_uuid ON audit_log(user_uuid);
CREATE INDEX idx_audit_log_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_event_status ON audit_log(event_status);
CREATE INDEX idx_audit_log_ip_address ON audit_log(ip_address);

-- ==============================================================================
-- TABLE: key_rotation_history
-- Track key rotation for security auditing
-- ==============================================================================

CREATE TABLE IF NOT EXISTS key_rotation_history (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- User context
    user_uuid UUID NOT NULL REFERENCES wrapped_keys(user_uuid) ON DELETE CASCADE,

    -- Old key details (for rollback capability - short retention)
    old_wrapped_key BYTEA NOT NULL,
    old_key_version INTEGER NOT NULL,
    old_kms_key_id VARCHAR(255) NOT NULL,

    -- New key details
    new_key_version INTEGER NOT NULL,
    new_kms_key_id VARCHAR(255) NOT NULL,

    -- Rotation metadata
    rotation_reason VARCHAR(255), -- 'scheduled', 'security_incident', 'manual', etc.
    rotated_by VARCHAR(255),
    rotated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Auto-delete old keys after 30 days (security best practice)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() + INTERVAL '30 days'),

    -- Constraints
    CONSTRAINT key_versions_different CHECK (old_key_version != new_key_version),
    CONSTRAINT new_version_greater CHECK (new_key_version > old_key_version)
);

-- Indexes
CREATE INDEX idx_key_rotation_user_uuid ON key_rotation_history(user_uuid);
CREATE INDEX idx_key_rotation_expires_at ON key_rotation_history(expires_at);

-- ==============================================================================
-- TABLE: gdpr_requests
-- Track GDPR requests for compliance
-- ==============================================================================

CREATE TABLE IF NOT EXISTS gdpr_requests (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- User context
    user_uuid UUID NOT NULL REFERENCES wrapped_keys(user_uuid) ON DELETE SET NULL,

    -- Request details
    request_type VARCHAR(50) NOT NULL, -- 'access', 'erasure', 'portability', 'rectification'
    request_status VARCHAR(50) NOT NULL DEFAULT 'pending', -- 'pending', 'processing', 'completed', 'failed'

    -- Request metadata
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    -- Requester information
    requester_ip INET,
    requester_email VARCHAR(255),

    -- Processing details
    processing_notes TEXT,
    data_export_url TEXT, -- For data portability requests

    -- Constraints
    CONSTRAINT request_type_valid CHECK (request_type IN ('access', 'erasure', 'portability', 'rectification', 'restriction')),
    CONSTRAINT request_status_valid CHECK (request_status IN ('pending', 'processing', 'completed', 'failed', 'cancelled'))
);

-- Indexes
CREATE INDEX idx_gdpr_requests_user_uuid ON gdpr_requests(user_uuid);
CREATE INDEX idx_gdpr_requests_type ON gdpr_requests(request_type);
CREATE INDEX idx_gdpr_requests_status ON gdpr_requests(request_status);
CREATE INDEX idx_gdpr_requests_requested_at ON gdpr_requests(requested_at DESC);

-- ==============================================================================
-- FUNCTIONS: Automatic timestamp updates
-- ==============================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers
CREATE TRIGGER update_wrapped_keys_updated_at BEFORE UPDATE ON wrapped_keys
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_blockchain_wallets_updated_at BEFORE UPDATE ON blockchain_wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- FUNCTIONS: Automatic expiration cleanup (GDPR)
-- ==============================================================================

CREATE OR REPLACE FUNCTION cleanup_expired_keys()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Soft delete expired keys
    UPDATE wrapped_keys
    SET is_deleted = TRUE, deleted_at = NOW()
    WHERE expires_at < NOW() AND is_deleted = FALSE;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    -- Log cleanup in audit trail
    INSERT INTO audit_log (event_type, event_action, event_status, event_data, created_at)
    VALUES (
        'cleanup',
        'delete',
        'success',
        json_build_object('deleted_count', deleted_count, 'reason', 'expiration'),
        NOW()
    );

    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- FUNCTIONS: Automatic key rotation history cleanup
-- ==============================================================================

CREATE OR REPLACE FUNCTION cleanup_expired_rotation_history()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Permanently delete expired rotation history
    DELETE FROM key_rotation_history
    WHERE expires_at < NOW();

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- VIEWS: Useful queries
-- ==============================================================================

-- Active users (not deleted, not expired)
CREATE OR REPLACE VIEW active_users AS
SELECT
    user_uuid,
    key_version,
    created_at,
    last_accessed_at,
    expires_at
FROM wrapped_keys
WHERE is_deleted = FALSE AND (expires_at IS NULL OR expires_at > NOW());

-- Recent audit events (last 7 days)
CREATE OR REPLACE VIEW recent_audit_events AS
SELECT
    id,
    user_uuid,
    event_type,
    event_action,
    event_status,
    ip_address,
    created_at
FROM audit_log
WHERE created_at > NOW() - INTERVAL '7 days'
ORDER BY created_at DESC;

-- ==============================================================================
-- GRANTS: Restrict access (principle of least privilege)
-- ==============================================================================

-- Create application role (used by backend)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'zeropay_app') THEN
        CREATE ROLE zeropay_app WITH LOGIN PASSWORD 'CHANGE_ME_IN_PRODUCTION';
    END IF;
END $$;

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON wrapped_keys TO zeropay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON blockchain_wallets TO zeropay_app;
GRANT SELECT, INSERT ON audit_log TO zeropay_app;
GRANT SELECT, INSERT ON key_rotation_history TO zeropay_app;
GRANT SELECT, INSERT, UPDATE ON gdpr_requests TO zeropay_app;
GRANT SELECT ON active_users TO zeropay_app;
GRANT SELECT ON recent_audit_events TO zeropay_app;

-- Grant sequence usage (for ID generation)
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO zeropay_app;

-- ==============================================================================
-- COMMENTS: Documentation
-- ==============================================================================

COMMENT ON TABLE wrapped_keys IS 'Stores KMS-wrapped encryption keys for factor digests (zero-knowledge: never stores actual digests)';
COMMENT ON TABLE blockchain_wallets IS 'Stores verified blockchain wallet addresses linked to user UUIDs';
COMMENT ON TABLE audit_log IS 'Security audit trail for GDPR/PSD3 compliance';
COMMENT ON TABLE key_rotation_history IS 'Tracks key rotation events for security auditing';
COMMENT ON TABLE gdpr_requests IS 'Tracks GDPR data subject requests (access, erasure, portability)';

-- ==============================================================================
-- END OF SCHEMA
-- ==============================================================================
