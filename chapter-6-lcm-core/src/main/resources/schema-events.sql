-- Events table for event store (VNF and OP_OCC aggregates).
-- aggregate_type distinguishes streams: 'VNF' | 'OP_OCC'.
CREATE TABLE IF NOT EXISTS events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL UNIQUE,
    aggregate_id     VARCHAR(36) NOT NULL,
    aggregate_type   VARCHAR(32) NOT NULL DEFAULT 'VNF',
    version         INT NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         TEXT NOT NULL,
    event_timestamp TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_aggregate_version ON events (aggregate_id, aggregate_type, version);

-- If table already exists without aggregate_type (legacy), run:
-- ALTER TABLE events ADD COLUMN IF NOT EXISTS aggregate_type VARCHAR(32) NOT NULL DEFAULT 'VNF';
-- CREATE INDEX IF NOT EXISTS idx_events_aggregate_version ON events (aggregate_id, aggregate_type, version);
