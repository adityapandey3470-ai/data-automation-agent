CREATE TABLE IF NOT EXISTS async_jobs (
    job_id         VARCHAR(40) PRIMARY KEY,
    user_id        BIGINT,
    job_type       VARCHAR(30)  NOT NULL,
    title          VARCHAR(300),
    status         VARCHAR(20)  NOT NULL,
    progress       INT          NOT NULL DEFAULT 0,
    status_message VARCHAR(1000),
    result_json    TEXT,
    error_message  TEXT,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    completed_at   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_async_jobs_user_created ON async_jobs (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_async_jobs_status_updated ON async_jobs (status, updated_at);
