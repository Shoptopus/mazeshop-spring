INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('contents.view', now(), now()),
    ('contents.manage', now(), now()),
    ('pages.view', now(), now()),
    ('pages.manage', now(), now())
ON CONFLICT (name) DO NOTHING;
