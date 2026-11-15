-- =====================================================================================
-- V3: tipologiche, ruoli e permessi dinamici, vendite (cassa), registro attività.
--
-- Migrazione "expand": aggiunge e popola, NON elimina le colonne vecchie
-- (products.category / unit / origin, users.role). Così le versioni precedenti
-- dell'applicazione continuano a funzionare durante un rolling update e un eventuale
-- rollback. Le colonne vecchie si potranno eliminare in una migrazione successiva (V4)
-- quando tutti i pod girano sulla nuova versione.
-- =====================================================================================

-- ------------------------------ TIPOLOGICHE ------------------------------------------
CREATE TABLE product_categories (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(60) NOT NULL UNIQUE,
    name       VARCHAR(80) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_product_categories_name ON product_categories (lower(name));

CREATE TABLE units (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(60) NOT NULL UNIQUE,
    name           VARCHAR(80) NOT NULL,
    short_label    VARCHAR(20) NOT NULL,
    allow_decimals BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order     INT         NOT NULL DEFAULT 0,
    active         BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_units_name ON units (lower(name));

CREATE TABLE vat_rates (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(60)  NOT NULL UNIQUE,
    name       VARCHAR(80)  NOT NULL,
    rate       NUMERIC(5,2) NOT NULL CHECK (rate >= 0 AND rate <= 100),
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_vat_rates_name ON vat_rates (lower(name));

CREATE TABLE origins (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(60) NOT NULL UNIQUE,
    name       VARCHAR(80) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_origins_name ON origins (lower(name));

CREATE TABLE waste_reasons (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(60) NOT NULL UNIQUE,
    name       VARCHAR(80) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_waste_reasons_name ON waste_reasons (lower(name));

CREATE TABLE payment_methods (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(60) NOT NULL UNIQUE,
    name       VARCHAR(80) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_payment_methods_name ON payment_methods (lower(name));

CREATE TABLE customer_types (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(60) NOT NULL UNIQUE,
    name       VARCHAR(80) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_customer_types_name ON customer_types (lower(name));

-- Valori iniziali (l'amministratore può modificarli e aggiungerne altri)
INSERT INTO units (code, name, short_label, allow_decimals, sort_order) VALUES
    ('KG',     'Chilogrammo', 'kg',        TRUE,  1),
    ('PIECE',  'Pezzo',       'pz',        FALSE, 2),
    ('CRATE',  'Cassetta',    'cassetta',  TRUE,  3),
    ('BUNCH',  'Mazzo',       'mazzo',     FALSE, 4),
    ('PUNNET', 'Vaschetta',   'vaschetta', FALSE, 5);

INSERT INTO product_categories (code, name, sort_order) VALUES
    ('FRUTTA', 'Frutta', 1), ('VERDURA', 'Verdura', 2),
    ('ERBE_AROMATICHE', 'Erbe aromatiche', 3), ('ALTRO', 'Altro', 4);

-- Aliquote di esempio: vanno verificate con il commercialista per ogni prodotto
INSERT INTO vat_rates (code, name, rate, sort_order) VALUES
    ('IVA_4', 'IVA 4%', 4, 1), ('IVA_5', 'IVA 5%', 5, 2),
    ('IVA_10', 'IVA 10%', 10, 3), ('IVA_22', 'IVA 22%', 22, 4);

INSERT INTO waste_reasons (code, name, sort_order) VALUES
    ('TROPPO_MATURE', 'Troppo mature', 1), ('SCADUTO', 'Scaduto', 2),
    ('DANNEGGIATO', 'Danneggiato', 3), ('APPASSITO', 'Appassito', 4), ('ALTRO', 'Altro', 5);

INSERT INTO payment_methods (code, name, sort_order) VALUES
    ('CASH', 'Contanti', 1), ('CARD', 'Carta / Bancomat', 2), ('OTHER', 'Altro', 3);

INSERT INTO customer_types (code, name, sort_order) VALUES
    ('PRIVATE', 'Privato', 1), ('RESTAURANT', 'Ristorante / locale', 2), ('BUSINESS', 'Azienda / ente', 3);

-- Valori già presenti nei dati esistenti e non coperti dai valori iniziali
INSERT INTO product_categories (code, name, sort_order)
SELECT 'MIG_' || row_number() OVER (ORDER BY d.n), d.n, 100
FROM (SELECT DISTINCT ON (lower(trim(category))) trim(category) AS n
      FROM products ORDER BY lower(trim(category)), trim(category)) d
WHERE NOT EXISTS (SELECT 1 FROM product_categories pc WHERE lower(pc.name) = lower(d.n));

INSERT INTO origins (code, name, sort_order)
SELECT 'MIG_' || row_number() OVER (ORDER BY d.n), d.n, 100
FROM (SELECT DISTINCT ON (lower(trim(origin))) trim(origin) AS n
      FROM products WHERE origin IS NOT NULL AND trim(origin) <> ''
      ORDER BY lower(trim(origin)), trim(origin)) d;

INSERT INTO waste_reasons (code, name, sort_order)
SELECT 'MIG_' || row_number() OVER (ORDER BY d.n), d.n, 100
FROM (SELECT DISTINCT ON (lower(trim(reason))) trim(reason) AS n
      FROM stock_movements WHERE type = 'WASTE' AND reason IS NOT NULL AND trim(reason) <> ''
      ORDER BY lower(trim(reason)), trim(reason)) d
WHERE NOT EXISTS (SELECT 1 FROM waste_reasons w WHERE lower(w.name) = lower(d.n));

-- ------------------------------ PRODOTTI ---------------------------------------------
ALTER TABLE products ADD COLUMN category_id BIGINT REFERENCES product_categories (id);
ALTER TABLE products ADD COLUMN unit_id     BIGINT REFERENCES units (id);
ALTER TABLE products ADD COLUMN origin_id   BIGINT REFERENCES origins (id);
ALTER TABLE products ADD COLUMN vat_rate_id BIGINT REFERENCES vat_rates (id);

UPDATE products p SET category_id =
    (SELECT pc.id FROM product_categories pc WHERE lower(pc.name) = lower(trim(p.category)));
UPDATE products p SET unit_id = (SELECT u.id FROM units u WHERE u.code = p.unit);
UPDATE products p SET origin_id =
    (SELECT o.id FROM origins o WHERE lower(o.name) = lower(trim(p.origin)))
    WHERE p.origin IS NOT NULL AND trim(p.origin) <> '';
-- Aliquota assegnata a tutti i prodotti esistenti: 4% (tipica per l'ortofrutta fresca). Da verificare.
UPDATE products SET vat_rate_id = (SELECT id FROM vat_rates WHERE code = 'IVA_4');

ALTER TABLE products ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE products ALTER COLUMN unit_id     SET NOT NULL;
ALTER TABLE products ALTER COLUMN vat_rate_id SET NOT NULL;
-- le colonne vecchie restano ma non sono più obbligatorie (le scrive solo il codice precedente)
ALTER TABLE products ALTER COLUMN category DROP NOT NULL;
ALTER TABLE products ALTER COLUMN unit     DROP NOT NULL;

CREATE INDEX idx_products_category ON products (category_id);

ALTER TABLE customers ADD COLUMN type_id BIGINT REFERENCES customer_types (id);

-- ------------------------------ VENDITE (CASSA) --------------------------------------
CREATE TABLE sales (
    id                BIGSERIAL PRIMARY KEY,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(50),
    payment_method_id BIGINT        REFERENCES payment_methods (id),
    total             NUMERIC(12,2) NOT NULL DEFAULT 0,
    status            VARCHAR(10)   NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'VOIDED')),
    voided_at         TIMESTAMPTZ,
    voided_by         VARCHAR(50),
    void_reason       VARCHAR(200)
);
CREATE INDEX idx_sales_created ON sales (created_at DESC);
CREATE INDEX idx_sales_created_by ON sales (created_by, created_at DESC);

ALTER TABLE stock_movements ADD COLUMN sale_id         BIGINT REFERENCES sales (id);
ALTER TABLE stock_movements ADD COLUMN created_by      VARCHAR(50);
ALTER TABLE stock_movements ADD COLUMN voided          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stock_movements ADD COLUMN waste_reason_id BIGINT REFERENCES waste_reasons (id);
ALTER TABLE stock_movements ADD COLUMN vat_rate        NUMERIC(5,2);

UPDATE stock_movements m SET waste_reason_id =
    (SELECT w.id FROM waste_reasons w WHERE lower(w.name) = lower(trim(m.reason)))
    WHERE m.type = 'WASTE' AND m.reason IS NOT NULL;
-- aliquota "congelata" sulle vendite passate (da qui in poi la scrive l'applicazione a ogni vendita)
UPDATE stock_movements m SET vat_rate =
    (SELECT v.rate FROM vat_rates v JOIN products p ON p.vat_rate_id = v.id WHERE p.id = m.product_id)
    WHERE m.type = 'SALE';

CREATE INDEX idx_movements_sale ON stock_movements (sale_id) WHERE sale_id IS NOT NULL;

-- ------------------------------ RUOLI E PERMESSI -------------------------------------
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(300),
    system_role BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_roles_name ON roles (lower(name));

CREATE TABLE role_permissions (
    role_id    BIGINT      NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

INSERT INTO roles (code, name, description, system_role) VALUES
    ('ADMIN',     'Amministratore', 'Accesso completo: l''unico che gestisce utenti, ruoli e tipologiche', TRUE),
    ('MANAGER',   'Responsabile',   'Gestisce il negozio: carico, ordini, costi e report. Non cambia i prezzi di listino né gli utenti', FALSE),
    ('STAFF',     'Commesso',       'Vende, registra gli scarti e gestisce le prenotazioni. Non vede costi e margini', FALSE),
    ('WAREHOUSE', 'Magazziniere',   'Riceve la merce e gestisce gli ordini ai fornitori. Non usa la cassa', FALSE);

-- L'ADMIN ha sempre tutti i permessi (calcolati dall'applicazione): qui si elencano gli altri ruoli
INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
JOIN (VALUES
    ('MANAGER', 'PRODUCTS_READ'), ('MANAGER', 'SALES_CREATE'), ('MANAGER', 'SALES_PRICE_OVERRIDE'),
    ('MANAGER', 'SALES_VOID'), ('MANAGER', 'WASTE_CREATE'), ('MANAGER', 'STOCK_READ'),
    ('MANAGER', 'STOCK_RECEIVE'), ('MANAGER', 'COSTS_VIEW'), ('MANAGER', 'REPORTS_VIEW'),
    ('MANAGER', 'SUPPLIERS_READ'), ('MANAGER', 'SUPPLIERS_MANAGE'), ('MANAGER', 'SUPPLIER_ORDERS_MANAGE'),
    ('MANAGER', 'CUSTOMERS_MANAGE'), ('MANAGER', 'CUSTOMERS_DELETE'), ('MANAGER', 'RESERVATIONS_MANAGE'),
    ('MANAGER', 'AUDIT_VIEW'),
    ('STAFF', 'PRODUCTS_READ'), ('STAFF', 'SALES_CREATE'), ('STAFF', 'WASTE_CREATE'),
    ('STAFF', 'STOCK_READ'), ('STAFF', 'CUSTOMERS_MANAGE'), ('STAFF', 'RESERVATIONS_MANAGE'),
    ('WAREHOUSE', 'PRODUCTS_READ'), ('WAREHOUSE', 'STOCK_READ'), ('WAREHOUSE', 'STOCK_RECEIVE'),
    ('WAREHOUSE', 'WASTE_CREATE'), ('WAREHOUSE', 'SUPPLIERS_READ'),
    ('WAREHOUSE', 'SUPPLIER_ORDERS_MANAGE'), ('WAREHOUSE', 'COSTS_VIEW')
) AS p(role_code, permission) ON p.role_code = r.code;

ALTER TABLE users ADD COLUMN role_id BIGINT REFERENCES roles (id);
UPDATE users u SET role_id = (SELECT r.id FROM roles r WHERE r.code = u.role);
ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;
-- la colonna "role" resta (la usa il codice precedente) ma non è più obbligatoria né limitata a 2 valori
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;
CREATE INDEX idx_users_role ON users (role_id);

-- ------------------------------ REGISTRO ATTIVITÀ ------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    username    VARCHAR(50)  NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   VARCHAR(40),
    detail      VARCHAR(500)
);
CREATE INDEX idx_audit_created ON audit_log (created_at DESC, id DESC);
