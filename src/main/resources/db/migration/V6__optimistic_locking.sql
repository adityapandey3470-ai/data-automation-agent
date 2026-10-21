-- V6: Optimistic locking for concurrent session edits and project saves
ALTER TABLE working_sessions ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
