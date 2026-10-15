CREATE TABLE registration_terms (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    required BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT uk_registration_terms_code_version UNIQUE (code, version),
    CONSTRAINT ck_registration_terms_nonblank CHECK (
        LENGTH(TRIM(code)) > 0 AND LENGTH(TRIM(version)) > 0
        AND LENGTH(TRIM(title)) > 0 AND LENGTH(TRIM(content)) > 0
    )
);
CREATE INDEX idx_registration_terms_active ON registration_terms (active);
CREATE TABLE registration_consents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    oauth_account_id BIGINT NOT NULL,
    term_id UUID NOT NULL,
    agreed BOOLEAN NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_registration_consents_user_term UNIQUE (user_id, term_id),
    CONSTRAINT fk_registration_consents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_registration_consents_account FOREIGN KEY (oauth_account_id) REFERENCES oauth_accounts (id),
    CONSTRAINT fk_registration_consents_term FOREIGN KEY (term_id) REFERENCES registration_terms (id)
);
