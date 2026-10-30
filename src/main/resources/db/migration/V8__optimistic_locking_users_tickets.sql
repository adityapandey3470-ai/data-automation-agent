-- V8: Add optimistic locking version column to users and support_tickets tables
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
