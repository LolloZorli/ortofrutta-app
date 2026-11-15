-- Utenti
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Catalogo prodotti
CREATE TABLE products (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120)  NOT NULL,
    category   VARCHAR(60)   NOT NULL,
    unit       VARCHAR(10)   NOT NULL CHECK (unit IN ('KG', 'PIECE', 'CRATE')),
    sale_price NUMERIC(10,2) NOT NULL CHECK (sale_price >= 0),
    min_stock  NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    origin     VARCHAR(80),
    active     BOOLEAN       NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_products_name ON products (lower(name));

-- Fornitori
CREATE TABLE suppliers (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(120) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(120),
    notes VARCHAR(500)
);
CREATE UNIQUE INDEX uq_suppliers_name ON suppliers (lower(name));

-- Lotti: ogni partita arrivata, con costo e scadenza
CREATE TABLE lots (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT        NOT NULL REFERENCES products (id),
    supplier_id        BIGINT        REFERENCES suppliers (id),
    received_at        DATE          NOT NULL,
    expires_at         DATE,
    initial_quantity   NUMERIC(12,3) NOT NULL CHECK (initial_quantity > 0),
    remaining_quantity NUMERIC(12,3) NOT NULL,
    unit_cost          NUMERIC(10,2) NOT NULL CHECK (unit_cost >= 0),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_lot_remaining
        CHECK (remaining_quantity >= 0 AND remaining_quantity <= initial_quantity)
);
CREATE INDEX idx_lots_product_available
    ON lots (product_id, received_at, id) WHERE remaining_quantity > 0;

-- Movimenti di magazzino (storico)
CREATE TABLE stock_movements (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT        NOT NULL REFERENCES products (id),
    lot_id     BIGINT        REFERENCES lots (id),
    type       VARCHAR(10)   NOT NULL CHECK (type IN ('RECEIPT', 'SALE', 'WASTE')),
    quantity   NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2),
    unit_cost  NUMERIC(10,2),
    reason     VARCHAR(120),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_movements_created ON stock_movements (created_at DESC);
CREATE INDEX idx_movements_product_type ON stock_movements (product_id, type, created_at);

-- Ordini ai fornitori
CREATE TABLE supplier_orders (
    id          BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT      NOT NULL REFERENCES suppliers (id),
    status      VARCHAR(12) NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'RECEIVED', 'CANCELLED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,
    received_at TIMESTAMPTZ,
    notes       VARCHAR(500)
);

CREATE TABLE supplier_order_lines (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT        NOT NULL REFERENCES supplier_orders (id) ON DELETE CASCADE,
    product_id BIGINT        NOT NULL REFERENCES products (id),
    quantity   NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_cost  NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0)
);

-- Clienti e prenotazioni
CREATE TABLE customers (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(120) NOT NULL,
    phone VARCHAR(30),
    notes VARCHAR(500)
);

CREATE TABLE customer_orders (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customers (id),
    status      VARCHAR(12) NOT NULL CHECK (status IN ('NEW', 'READY', 'PICKED_UP', 'CANCELLED')),
    pickup_date DATE        NOT NULL,
    notes       VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customer_order_lines (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT        NOT NULL REFERENCES customer_orders (id) ON DELETE CASCADE,
    product_id BIGINT        NOT NULL REFERENCES products (id),
    quantity   NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0)
);
