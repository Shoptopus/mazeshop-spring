INSERT INTO permissions(name, created_at, updated_at) VALUES
    ('notifications.view', now(), now()),
    ('notifications.manage', now(), now()),
    ('emails.manage', now(), now())
ON CONFLICT (name) DO NOTHING;
