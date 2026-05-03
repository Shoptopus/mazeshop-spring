INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('ai.manage', now(), now())
ON CONFLICT (name) DO NOTHING;
