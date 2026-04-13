CREATE TABLE email_otps (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL,
    otp_hash    VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_otps_email      ON email_otps(email);
CREATE INDEX idx_email_otps_expires_at ON email_otps(expires_at);
