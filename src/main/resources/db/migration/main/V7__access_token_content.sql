ALTER TABLE access_tokens
    ADD COLUMN IF NOT EXISTS content JSONB;
