INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('reports.view', now(), now())
ON CONFLICT (name) DO NOTHING;
