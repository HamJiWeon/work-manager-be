ALTER TABLE oauth_accounts
    ADD CONSTRAINT ck_oauth_accounts_provider
        CHECK (provider IN ('GOOGLE', 'GITHUB'));
