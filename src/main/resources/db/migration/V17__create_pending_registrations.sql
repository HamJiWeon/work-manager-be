CREATE TABLE pending_registrations (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    profile_image_url VARCHAR(255),
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_pending_registrations_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_pending_registrations_token_hash_length CHECK (LENGTH(token_hash) = 64),
    CONSTRAINT ck_pending_registrations_provider CHECK (provider IN ('GOOGLE', 'GITHUB'))
);
CREATE INDEX idx_pending_registrations_expires_at ON pending_registrations (expires_at);
