ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(254);
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD COLUMN customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL;
CREATE UNIQUE INDEX uq_users_email ON users(lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_users_customer ON users(customer_id) WHERE customer_id IS NOT NULL;

INSERT INTO roles (code, name, description, system_role)
VALUES ('CUSTOMER', 'Cliente', 'Accede al catalogo e gestisce solo i propri ordini', TRUE);

INSERT INTO role_permissions(role_id, permission)
SELECT id, permission FROM roles CROSS JOIN (VALUES
  ('CATALOG_READ'), ('CUSTOMER_ORDER_CREATE'), ('CUSTOMER_ORDER_READ')
) AS p(permission) WHERE code = 'CUSTOMER';

UPDATE roles SET name='Cassiere', description='Gestisce la cassa, gli scarti e le prenotazioni dei clienti' WHERE code='STAFF';
INSERT INTO role_permissions(role_id, permission)
SELECT id, 'PRODUCTS_WRITE' FROM roles WHERE code='MANAGER'
ON CONFLICT DO NOTHING;
