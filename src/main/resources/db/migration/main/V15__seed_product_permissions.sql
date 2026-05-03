INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('products.view', now(), now()),
    ('products.manage', now(), now())
ON CONFLICT (name) DO NOTHING;
