ALTER TABLE file_contents
    ADD COLUMN IF NOT EXISTS original_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS type VARCHAR(120),
    ADD COLUMN IF NOT EXISTS fileable_type VARCHAR(120),
    ADD COLUMN IF NOT EXISTS fileable_id UUID;

CREATE INDEX IF NOT EXISTS idx_file_contents_fileable
    ON file_contents(fileable_type, fileable_id);

CREATE INDEX IF NOT EXISTS idx_file_contents_type
    ON file_contents(type);
