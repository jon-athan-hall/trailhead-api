-- Add verified flag to users table, defaulting to false for existing users.
ALTER TABLE users ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Store email verification tokens.
CREATE TABLE email_verification_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token       VARCHAR(255) NOT NULL,
    user_id     VARCHAR(36)     NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    confirmed_at DATETIME(6) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token (token),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id)
);
