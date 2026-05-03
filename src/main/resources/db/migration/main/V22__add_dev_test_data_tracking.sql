CREATE TABLE IF NOT EXISTS dev_seed_runs (
    id UUID PRIMARY KEY,
    seed_key VARCHAR(200) NOT NULL,
    source_dump VARCHAR(2000) NOT NULL,
    status VARCHAR(40) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_dev_seed_runs_seed_key ON dev_seed_runs(seed_key, started_at DESC);

CREATE TABLE IF NOT EXISTS dev_seed_records (
    run_id UUID NOT NULL REFERENCES dev_seed_runs(id) ON DELETE CASCADE,
    table_name VARCHAR(120) NOT NULL,
    record_id VARCHAR(120) NOT NULL,
    PRIMARY KEY (run_id, table_name, record_id)
);

CREATE INDEX IF NOT EXISTS idx_dev_seed_records_table ON dev_seed_records(table_name, record_id);

CREATE TABLE IF NOT EXISTS dev_seed_files (
    run_id UUID NOT NULL REFERENCES dev_seed_runs(id) ON DELETE CASCADE,
    relative_path VARCHAR(2000) NOT NULL,
    PRIMARY KEY (run_id, relative_path)
);
