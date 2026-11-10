-- OPTIONAL, PostgreSQL ONLY (H2 does not support expression indexes). Run manually after taking a backup.

-- 1) Case-insensitive unique email.
--    Hibernate's findByEmailIgnoreCase generates upper(email) = upper(?), so index UPPER(email) (it serves both lookup and uniqueness).
--    First make sure there are no duplicates:  SELECT upper(email), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_upper ON users (UPPER(email));

-- 2) Foreign keys (these tables had none, so deleting a user/dashboard left orphans). Clean orphans first.
DELETE FROM dashboard_collaborators WHERE dashboard_id NOT IN (SELECT dashboard_id FROM dashboards);
DELETE FROM dashboards              WHERE user_id NOT IN (SELECT id FROM users);
DELETE FROM async_jobs              WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT id FROM users);
DELETE FROM ai_usage_metrics        WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT id FROM users);

ALTER TABLE dashboards
    ADD CONSTRAINT fk_dashboards_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE dashboard_collaborators
    ADD CONSTRAINT fk_collab_dashboard FOREIGN KEY (dashboard_id) REFERENCES dashboards (dashboard_id) ON DELETE CASCADE;
ALTER TABLE async_jobs
    ADD CONSTRAINT fk_async_jobs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE ai_usage_metrics
    ADD CONSTRAINT fk_ai_metrics_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
