-- Bootstrap admin user if not exists
INSERT INTO users (
    id,
    email,
    name,
    approval_status,
    created_at,
    updated_at
) SELECT
    '7612a810-c65c-4047-96e3-3e1c1b425e87',
    'admin@test.com',
    'Admin User',
    'APPROVED',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE id = '7612a810-c65c-4047-96e3-3e1c1b425e87'
);
