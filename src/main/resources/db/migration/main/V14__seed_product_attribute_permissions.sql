INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('product_attributes.view', now(), now()),
    ('product_attributes.manage', now(), now())
ON CONFLICT (name) DO NOTHING;
