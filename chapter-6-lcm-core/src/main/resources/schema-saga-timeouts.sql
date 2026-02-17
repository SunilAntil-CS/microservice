-- Saga timeouts for persistent timeout handling (see FLOW.md §8).
CREATE TABLE IF NOT EXISTS saga_timeouts (
    id         BIGSERIAL PRIMARY KEY,
    saga_id    VARCHAR(36) NOT NULL,
    step       INT NOT NULL,
    execute_at TIMESTAMP NOT NULL,
    processed  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_saga_timeouts_execute_processed ON saga_timeouts (processed, execute_at);
