ALTER TABLE company_memberships
    ADD COLUMN approved BOOLEAN NOT NULL DEFAULT TRUE;

-- Manager can review staff and client records; cashiers can create supplier orders.
INSERT INTO role_permissions(role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('MANAGER', 'USERS_READ'),
    ('MANAGER', 'USERS_APPROVE'),
    ('STAFF', 'SUPPLIERS_READ'),
    ('STAFF', 'SUPPLIER_ORDER_CREATE')
) AS p(role_code, permission)
WHERE r.code = p.role_code
ON CONFLICT DO NOTHING;
