-- Outbox table for policy events (command side).
-- Debezium captures this table and publishes to Kafka.
-- Atomicity: application writes event in same transaction as business write.

CREATE TABLE IF NOT EXISTS outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_outbox_created_at ON outbox (created_at);

-- Publication for Debezium (pgoutput plugin)
CREATE PUBLICATION dbz_publication FOR TABLE outbox;

COMMENT ON TABLE outbox IS 'Transactional outbox for policy events; consumed by Debezium.';
