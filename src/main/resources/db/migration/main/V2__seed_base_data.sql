INSERT INTO users (
    id, first_name, last_name, email, password, name, slug, email_verified_at, temporary, is_favorite, created_at, updated_at
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Super',
    'Admin',
    'superadmin@m.com',
    '$2a$10$d.DHuG9JyfQGT6vfL/OaL.XvZh.7zBvcaM9esW.ftNNhV.ti6XPdi',
    'Super Admin',
    'super-admin',
    NOW(),
    FALSE,
    FALSE,
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO payment_providers (name, enabled, test_mode, created_at, updated_at)
VALUES
    ('stripe', true, true, NOW(), NOW()),
    ('paypal', true, true, NOW(), NOW()),
    ('amazon', true, true, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO payment_provider_configs (payment_provider_id, setting, value, test_value, public_value, created_at, updated_at)
SELECT id, 'public_key', null, 'test_public', true, NOW(), NOW() FROM payment_providers WHERE name = 'stripe'
ON CONFLICT DO NOTHING;
