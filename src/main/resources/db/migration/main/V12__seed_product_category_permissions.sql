INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('product_categories.view', now(), now()),
    ('product_categories.manage', now(), now())
ON CONFLICT (name) DO NOTHING;
