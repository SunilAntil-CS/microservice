-- Read-side table for listing VNFs (GET /api/vnfs).
CREATE TABLE IF NOT EXISTS vnf_index (
    vnf_id     VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_vnf_index_vnf_id ON vnf_index (vnf_id);
