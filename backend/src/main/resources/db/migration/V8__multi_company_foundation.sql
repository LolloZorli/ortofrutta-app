-- Multi-company foundation. Existing records are assigned to the original shop.
CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO companies (id, name, slug) VALUES (1, 'Ortofrutta', 'ortofrutta');
SELECT setval(pg_get_serial_sequence('companies', 'id'), (SELECT max(id) FROM companies));
ALTER TABLE users ADD COLUMN display_name VARCHAR(120);

-- Every business-owned table gets an explicit tenant key; users remain global identities.
ALTER TABLE products ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE suppliers ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE lots ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE stock_movements ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE supplier_orders ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE supplier_order_lines ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE customers ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE customer_orders ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE customer_order_lines ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE product_categories ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE units ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE vat_rates ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE origins ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE waste_reasons ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE payment_methods ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE customer_types ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE sales ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE cash_closings ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE cash_closings DROP CONSTRAINT cash_closings_closing_date_key;
CREATE UNIQUE INDEX uq_cash_closings_company_date ON cash_closings(company_id, closing_date);
ALTER TABLE roles ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);
ALTER TABLE audit_log ADD COLUMN company_id BIGINT NOT NULL DEFAULT 1 REFERENCES companies(id);

-- Role names/codes and catalog values are unique inside a shop, not globally.
ALTER TABLE roles DROP CONSTRAINT roles_code_key;
DROP INDEX uq_roles_name;
CREATE UNIQUE INDEX uq_roles_company_code ON roles(company_id, code);
CREATE UNIQUE INDEX uq_roles_company_name ON roles(company_id, lower(name));

-- Lookup codes are copied from the template shop at company registration.
-- Replace their original global UNIQUE constraints with tenant-local ones.
ALTER TABLE product_categories DROP CONSTRAINT product_categories_code_key;
ALTER TABLE units DROP CONSTRAINT units_code_key;
ALTER TABLE vat_rates DROP CONSTRAINT vat_rates_code_key;
ALTER TABLE origins DROP CONSTRAINT origins_code_key;
ALTER TABLE waste_reasons DROP CONSTRAINT waste_reasons_code_key;
ALTER TABLE payment_methods DROP CONSTRAINT payment_methods_code_key;
ALTER TABLE customer_types DROP CONSTRAINT customer_types_code_key;
CREATE UNIQUE INDEX uq_product_categories_company_code ON product_categories(company_id, code);
CREATE UNIQUE INDEX uq_units_company_code ON units(company_id, code);
CREATE UNIQUE INDEX uq_vat_rates_company_code ON vat_rates(company_id, code);
CREATE UNIQUE INDEX uq_origins_company_code ON origins(company_id, code);
CREATE UNIQUE INDEX uq_waste_reasons_company_code ON waste_reasons(company_id, code);
CREATE UNIQUE INDEX uq_payment_methods_company_code ON payment_methods(company_id, code);
CREATE UNIQUE INDEX uq_customer_types_company_code ON customer_types(company_id, code);

DROP INDEX uq_products_name;
CREATE UNIQUE INDEX uq_products_company_name ON products(company_id, lower(name));
DROP INDEX uq_products_barcode;
CREATE UNIQUE INDEX uq_products_company_barcode ON products(company_id, lower(barcode)) WHERE barcode IS NOT NULL;
DROP INDEX uq_suppliers_name;
CREATE UNIQUE INDEX uq_suppliers_company_name ON suppliers(company_id, lower(name));
DROP INDEX uq_product_categories_name;
CREATE UNIQUE INDEX uq_product_categories_company_name ON product_categories(company_id, lower(name));
DROP INDEX uq_units_name;
CREATE UNIQUE INDEX uq_units_company_name ON units(company_id, lower(name));
DROP INDEX uq_vat_rates_name;
CREATE UNIQUE INDEX uq_vat_rates_company_name ON vat_rates(company_id, lower(name));
DROP INDEX uq_origins_name;
CREATE UNIQUE INDEX uq_origins_company_name ON origins(company_id, lower(name));
DROP INDEX uq_waste_reasons_name;
CREATE UNIQUE INDEX uq_waste_reasons_company_name ON waste_reasons(company_id, lower(name));
DROP INDEX uq_payment_methods_name;
CREATE UNIQUE INDEX uq_payment_methods_company_name ON payment_methods(company_id, lower(name));
DROP INDEX uq_customer_types_name;
CREATE UNIQUE INDEX uq_customer_types_company_name ON customer_types(company_id, lower(name));

-- A global login can have a different role and a different customer profile in every shop.
DROP INDEX uq_users_customer;
CREATE TABLE company_memberships (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id),
    customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_membership_user_company UNIQUE (user_id, company_id),
    CONSTRAINT uq_membership_customer_company UNIQUE (customer_id, company_id)
);
CREATE INDEX idx_membership_company_role ON company_memberships(company_id, role_id);
INSERT INTO company_memberships(company_id, user_id, role_id, customer_id)
SELECT 1, id, role_id, customer_id FROM users;

CREATE INDEX idx_products_company_active_name ON products(company_id, active, name);
CREATE INDEX idx_customers_company_name ON customers(company_id, name);
CREATE INDEX idx_suppliers_company_name ON suppliers(company_id, name);
CREATE INDEX idx_sales_company_created ON sales(company_id, created_at DESC);
CREATE INDEX idx_audit_company_created ON audit_log(company_id, created_at DESC, id DESC);
