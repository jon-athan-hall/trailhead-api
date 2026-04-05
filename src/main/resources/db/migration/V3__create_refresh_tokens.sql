-- Long-lived tokens that users exchange for new access tokens without re-entering a password.
CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    token      VARCHAR(255) NOT NULL, -- UUID string
    user_id    BIGINT       NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    -- Associated tokens are cleaned up when a user is deleted.
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- MySQL maintains a separate sorted data structure that maps user_id to their row locations, to speed up searches.
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
