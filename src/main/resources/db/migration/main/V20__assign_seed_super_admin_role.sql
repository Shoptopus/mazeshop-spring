INSERT INTO model_has_roles(role_id, user_id)
SELECT r.id, '11111111-1111-1111-1111-111111111111'::uuid
FROM roles r
JOIN users u ON u.id = '11111111-1111-1111-1111-111111111111'::uuid
WHERE r.name = 'super_admin'
  AND u.deleted_at IS NULL
ON CONFLICT DO NOTHING;
