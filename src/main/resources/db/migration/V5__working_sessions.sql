-- V5: Database-backed working sessions

-- Seed anonymous user with ID 0 for unauthenticated sessions
INSERT INTO users (id, email, password_hash, name, role, enabled, created_at, updated_at)
SELECT 0, 'anonymous@tabufinery.io', '$2a$10$none', 'Anonymous User', 'ROLE_USER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 0);

CREATE TABLE IF NOT EXISTS working_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(255),
    file_size BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    workbook_data_json TEXT NOT NULL,
    history_json TEXT,
    current_index INT DEFAULT -1,
    last_accessed TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_working_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_working_sessions_user_id ON working_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_working_sessions_last_accessed ON working_sessions(last_accessed);
CREATE INDEX IF NOT EXISTS idx_working_sessions_user_file ON working_sessions(user_id, file_id);
