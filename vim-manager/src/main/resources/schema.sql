-- Idempotency: processed commands (message_id from vim.commands)
CREATE TABLE IF NOT EXISTS processed_commands (
    id          BIGSERIAL PRIMARY KEY,
    message_id  VARCHAR(36) NOT NULL UNIQUE,
    processed_at TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_processed_commands_message_id ON processed_commands (message_id);

-- Outbox for vim.replies (transactional outbox pattern)
CREATE TABLE IF NOT EXISTS outbox (
    id              BIGSERIAL PRIMARY KEY,
    message_id      VARCHAR(36) NOT NULL UNIQUE,
    destination     VARCHAR(128) NOT NULL,
    message_type    VARCHAR(128) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL,
    next_retry_at   TIMESTAMP NOT NULL,
    last_error      VARCHAR(2048)
);
CREATE INDEX IF NOT EXISTS idx_outbox_status_next_retry ON outbox (status, next_retry_at);
