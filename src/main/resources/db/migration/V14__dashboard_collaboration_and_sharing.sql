CREATE TABLE IF NOT EXISTS dashboard_collaborators (
    id VARCHAR(64) PRIMARY KEY,
    dashboard_id VARCHAR(64) NOT NULL,
    owner_id BIGINT NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    shared_with_email VARCHAR(255) NOT NULL,
    shared_with_user_id BIGINT,
    dashboard_name VARCHAR(255) NOT NULL,
    can_customize BOOLEAN NOT NULL DEFAULT FALSE,
    can_invite BOOLEAN NOT NULL DEFAULT FALSE,
    invitation_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dashboard_collab UNIQUE (dashboard_id, shared_with_email)
);

CREATE INDEX idx_collab_shared_email ON dashboard_collaborators(shared_with_email);
CREATE INDEX idx_collab_owner_id ON dashboard_collaborators(owner_id);
CREATE INDEX idx_collab_dashboard_id ON dashboard_collaborators(dashboard_id);
