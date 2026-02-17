-- =============================================================================
-- Order Service schema: domain tables + Eventuate Tram (saga, messaging)
-- =============================================================================
-- Domain tables are created by JPA (orders, order_line_items).
-- Eventuate Tram tables are required for saga orchestration and outbox.
-- When using H2, Tram may create these; for MySQL, run this or use Tram's DDL.
-- =============================================================================

-- Outbox: messages to be published to Kafka (CDC or polling reads these)
CREATE TABLE IF NOT EXISTS message (
  id VARCHAR(255) PRIMARY KEY,
  destination VARCHAR(255) NOT NULL,
  headers CLOB NOT NULL,
  payload CLOB NOT NULL,
  published INT DEFAULT 0,
  creation_time BIGINT
);

CREATE INDEX IF NOT EXISTS idx_message_published ON message(published);
CREATE INDEX IF NOT EXISTS idx_message_creation_time ON message(creation_time);

-- Idempotency: avoid processing the same message twice
CREATE TABLE IF NOT EXISTS received_messages (
  consumer_id VARCHAR(255) NOT NULL,
  message_id VARCHAR(255) NOT NULL,
  creation_time BIGINT,
  PRIMARY KEY (consumer_id, message_id)
);

-- Saga state: one row per saga instance
CREATE TABLE IF NOT EXISTS saga_instance (
  id VARCHAR(255) PRIMARY KEY,
  saga_type VARCHAR(255) NOT NULL,
  saga_id VARCHAR(255) NOT NULL,
  state_name VARCHAR(255) NOT NULL,
  last_request_id VARCHAR(255),
  saga_data_type VARCHAR(255),
  saga_data_json CLOB,
  end_state INT DEFAULT 0,
  compensating INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_saga_instance_saga_type_saga_id ON saga_instance(saga_type, saga_id);

-- Saga participant state (per step)
CREATE TABLE IF NOT EXISTS saga_instance_participants (
  saga_type VARCHAR(255) NOT NULL,
  saga_id VARCHAR(255) NOT NULL,
  destination VARCHAR(255) NOT NULL,
  resource VARCHAR(255) NOT NULL,
  saga_instance_id VARCHAR(255) NOT NULL,
  PRIMARY KEY (saga_type, saga_id, destination, resource)
);
