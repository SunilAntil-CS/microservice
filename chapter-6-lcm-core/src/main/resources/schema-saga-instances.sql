-- Saga instances table for saga orchestrator (see FLOW.md §7).
-- operation_id links to LCM operation occurrence (ETSI) for completion/failure updates.
CREATE TABLE IF NOT EXISTS saga_instances (
    id           BIGSERIAL PRIMARY KEY,
    saga_id      VARCHAR(36) NOT NULL UNIQUE,
    vnf_id       VARCHAR(64) NOT NULL,
    operation_id VARCHAR(36),
    saga_type    VARCHAR(64) NOT NULL,
    current_step INT NOT NULL,
    saga_state   TEXT,
    status       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    updated_at   TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_saga_instances_saga_id ON saga_instances (saga_id);
CREATE INDEX IF NOT EXISTS idx_saga_instances_vnf_id_status ON saga_instances (vnf_id, status);
