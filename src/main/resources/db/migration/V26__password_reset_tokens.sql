CREATE TABLE password_reset_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_prt_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_prt_token_hash ON password_reset_tokens (token_hash);
CREATE INDEX idx_prt_expires_at ON password_reset_tokens (expires_at);
