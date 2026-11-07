CREATE TABLE IF NOT EXISTS dashboards (
    dashboard_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_id VARCHAR(64),
    file_name VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    sheet_name VARCHAR(255),
    widgets_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dashboards_user_id ON dashboards(user_id);
CREATE INDEX IF NOT EXISTS idx_dashboards_created_at ON dashboards(created_at);
