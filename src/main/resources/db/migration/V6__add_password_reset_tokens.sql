CREATE TABLE password_reset_tokens (
                                       id         UUID PRIMARY KEY,
                                       user_id    UUID         NOT NULL,
                                       token_hash VARCHAR(255) NOT NULL,
                                       expires_at TIMESTAMPTZ  NOT NULL,
                                       used       BOOLEAN      NOT NULL DEFAULT FALSE,
                                       created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);