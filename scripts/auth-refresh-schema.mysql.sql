-- Preparation only. Execute on a selected NEW test database, with UTC connection timezone.
-- No USE, DROP, or IF NOT EXISTS: existing incompatible tables must fail visibly.
-- Requires existing users table. Do not run on the old local auth schema.
CREATE TABLE auth_sessions (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 token_version INT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 revoked_at DATETIME(6) NULL,
 INDEX idx_auth_sessions_user (user_id),
 INDEX idx_auth_sessions_expiry (expires_at),
 CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE refresh_tokens (
 token_hash CHAR(64) NOT NULL PRIMARY KEY,
 session_id VARCHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 used_at DATETIME(6) NULL,
 INDEX idx_refresh_tokens_session (session_id),
 CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES auth_sessions(id)
) ENGINE=InnoDB;
