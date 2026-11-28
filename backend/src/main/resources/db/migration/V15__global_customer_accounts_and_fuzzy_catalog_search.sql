CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE users ADD COLUMN customer_account BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN phone VARCHAR(30);

UPDATE users u
SET customer_account = TRUE
FROM roles r
WHERE r.id = u.role_id AND r.code = 'CUSTOMER';

WITH customer_profiles AS (
    SELECT DISTINCT ON (m.user_id) m.user_id, c.name, c.phone
    FROM company_memberships m
    JOIN roles r ON r.id = m.role_id AND r.code = 'CUSTOMER'
    JOIN customers c ON c.id = m.customer_id AND c.company_id = m.company_id
    WHERE m.customer_id IS NOT NULL
    ORDER BY m.user_id, m.company_id
)
UPDATE users u
SET display_name = COALESCE(u.display_name, customer_profiles.name),
    phone = COALESCE(u.phone, customer_profiles.phone)
FROM customer_profiles
WHERE customer_profiles.user_id = u.id;

CREATE INDEX idx_products_active_name_trgm
    ON products USING GIN (lower(name) gin_trgm_ops) WHERE active;
CREATE INDEX idx_product_categories_name_trgm
    ON product_categories USING GIN (lower(name) gin_trgm_ops);
