CREATE TABLE IF NOT EXISTS event_logs (
    id UUID PRIMARY KEY,
    message VARCHAR(200) NOT NULL,
    type INTEGER NOT NULL,
    eventable_type VARCHAR(255),
    eventable_id VARCHAR(255),
    data TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
