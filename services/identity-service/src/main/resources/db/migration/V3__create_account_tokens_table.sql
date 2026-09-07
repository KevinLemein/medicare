CREATE TABLE account_tokens (
                                id           UUID PRIMARY KEY,
                                user_id      UUID NOT NULL,
                                type         VARCHAR(30)  NOT NULL,
                                token_hash   VARCHAR(255) NOT NULL,
                                expires_at   TIMESTAMPTZ  NOT NULL,
                                used_at      TIMESTAMPTZ,
                                created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

                                CONSTRAINT fk_account_tokens_user
                                    FOREIGN KEY (user_id) REFERENCES users (id),
                                CONSTRAINT chk_account_tokens_type
                                    CHECK (type IN ('ACTIVATION', 'PASSWORD_RESET'))
);

CREATE UNIQUE INDEX uq_account_tokens_token_hash ON account_tokens (token_hash);

CREATE INDEX idx_account_tokens_user_type_used
    ON account_tokens (user_id, type, used_at);