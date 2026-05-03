INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('users.view', now(), now()),
    ('users.manage', now(), now()),
    ('customers.view', now(), now()),
    ('carts.view', now(), now()),
    ('carts.manage', now(), now())
ON CONFLICT (name) DO NOTHING;
