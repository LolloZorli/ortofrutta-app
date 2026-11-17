ALTER TABLE sales ADD COLUMN customer_id BIGINT REFERENCES customers(id) ON DELETE SET NULL;
CREATE INDEX idx_sales_customer_created ON sales(customer_id, created_at DESC);
ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_type_check CHECK (type IN ('RECEIPT','SALE','WASTE','ADJUSTMENT'));

CREATE TABLE cash_closings (
    id BIGSERIAL PRIMARY KEY,
    closing_date DATE NOT NULL UNIQUE,
    expected_cash NUMERIC(12,2) NOT NULL,
    counted_cash NUMERIC(12,2) NOT NULL,
    note VARCHAR(500),
    closed_by VARCHAR(50),
    closed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
