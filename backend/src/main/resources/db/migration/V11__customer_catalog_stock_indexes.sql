-- Fast per-company stock availability for the customer catalog and order validation.
CREATE INDEX idx_lots_company_product_available
    ON lots (company_id, product_id) WHERE remaining_quantity > 0;

CREATE INDEX idx_customer_orders_company_status
    ON customer_orders (company_id, status);

CREATE INDEX idx_customer_order_lines_company_product_order
    ON customer_order_lines (company_id, product_id, order_id);
