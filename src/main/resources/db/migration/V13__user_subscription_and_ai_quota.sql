ALTER TABLE users ADD COLUMN IF NOT EXISTS subscription_tier VARCHAR(32) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_queries_used INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_queries_limit INT NOT NULL DEFAULT 10;
ALTER TABLE users ADD COLUMN IF NOT EXISTS total_tokens_used BIGINT NOT NULL DEFAULT 0;

UPDATE users SET subscription_tier = 'ENTERPRISE', ai_queries_limit = 999999 WHERE role = 'ROLE_ADMIN';

CREATE INDEX IF NOT EXISTS idx_users_subscription_tier ON users(subscription_tier);
