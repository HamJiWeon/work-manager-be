ALTER TABLE refresh_tokens
    ADD CONSTRAINT ck_refresh_tokens_token_hash_length
        CHECK (CHAR_LENGTH(token_hash) = 64);
