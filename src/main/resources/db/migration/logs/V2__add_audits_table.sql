CREATE TABLE IF NOT EXISTS audits (
    id UUID PRIMARY KEY,
    user_id UUID,
    operation VARCHAR(32) NOT NULL,
    entity_type VARCHAR(160) NOT NULL,
    entity_id VARCHAR(160),
    before_state JSONB,
    after_state JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audits_created_at ON audits (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audits_entity_type ON audits (entity_type);
CREATE INDEX IF NOT EXISTS idx_audits_user_id ON audits (user_id);
