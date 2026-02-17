-- Processed requests table for idempotency (see FLOW.md §6 and ProcessedRequest entity).
-- Run when using spring.jpa.hibernate.ddl-auto=validate.
CREATE TABLE IF NOT EXISTS processed_requests (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64) NOT NULL UNIQUE,
    response_cache  TEXT NOT NULL,
    processed_at    TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_processed_requests_request_id ON processed_requests (request_id);
