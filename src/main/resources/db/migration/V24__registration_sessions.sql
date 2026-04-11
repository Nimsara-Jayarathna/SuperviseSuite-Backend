CREATE TABLE registration_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(50),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_registration_sessions_token_hash ON registration_sessions(token_hash);
CREATE INDEX idx_registration_sessions_expires_at ON registration_sessions(expires_at);
