-- ------------------------------------------------------------------------------
-- MODULE 4: Idempotent Consumer — FMS schema
-- received_messages = deduplication table (PK = consumer_id + message_id)
-- trouble_ticket = business table (one ticket per unique event after idempotency)
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS received_messages (
  consumer_id   VARCHAR(255) NOT NULL,
  message_id    VARCHAR(255) NOT NULL,
  creation_time BIGINT,
  PRIMARY KEY (consumer_id, message_id)
);

CREATE TABLE IF NOT EXISTS trouble_ticket (
  id         VARCHAR(36) NOT NULL PRIMARY KEY,
  node_id    VARCHAR(255) NOT NULL,
  link_id    VARCHAR(255),
  type       VARCHAR(64) NOT NULL,
  status     VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP
);
