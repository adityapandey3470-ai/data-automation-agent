CREATE TABLE IF NOT EXISTS login_attempts (
    attempt_key      VARCHAR(64) PRIMARY KEY,
    attempts         INT    NOT NULL,
    first_attempt_at BIGINT NOT NULL,
    locked_until     BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_login_attempts_first ON login_attempts (first_attempt_at);
