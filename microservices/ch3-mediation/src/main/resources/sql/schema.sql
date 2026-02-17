-- ------------------------------------------------------------------------------
-- MODULE 3: Transactional Outbox — Database schema
-- Run this if you are not using JPA ddl-auto (e.g. production with Flyway/Liquibase).
-- H2 and most DBs: use appropriate types (e.g. CLOB for payload, BIGINT for creation_time).
-- ------------------------------------------------------------------------------

-- Business table: CDRs for legal audit
CREATE TABLE IF NOT EXISTS cdr (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  call_id VARCHAR(255) NOT NULL,
  subscriber_id VARCHAR(255) NOT NULL,
  duration_seconds BIGINT NOT NULL,
  cell_id VARCHAR(255),
  cost DECIMAL(10,2)
);

-- The Outbox table (same concept as in the Notes)
-- Relay reads WHERE published = 0, sends payload to Kafka, then sets published = 1
CREATE TABLE IF NOT EXISTS message (
  id VARCHAR(767) NOT NULL PRIMARY KEY,
  destination VARCHAR(1000) NOT NULL,
  headers VARCHAR(1000),
  payload CLOB NOT NULL,
  published SMALLINT DEFAULT 0,
  creation_time BIGINT
);

CREATE INDEX IF NOT EXISTS idx_message_published ON message(published);
CREATE INDEX IF NOT EXISTS idx_message_creation_time ON message(creation_time);
