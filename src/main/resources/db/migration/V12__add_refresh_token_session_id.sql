ALTER TABLE refresh_tokens
    ADD COLUMN session_id UUID;

UPDATE refresh_tokens
SET session_id = id;

ALTER TABLE refresh_tokens
    ALTER COLUMN session_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens (session_id);
