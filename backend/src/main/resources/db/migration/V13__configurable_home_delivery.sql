CREATE TABLE delivery_zones (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    city VARCHAR(120) NOT NULL,
    delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    minimum_order_subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_delivery_zone_fee CHECK (delivery_fee >= 0),
    CONSTRAINT chk_delivery_zone_minimum CHECK (minimum_order_subtotal >= 0)
);
CREATE UNIQUE INDEX uq_delivery_zones_company_city ON delivery_zones(company_id, lower(trim(city)));

CREATE TABLE delivery_slots (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    zone_id BIGINT NOT NULL REFERENCES delivery_zones(id),
    name VARCHAR(80) NOT NULL,
    max_orders INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_delivery_slot_capacity CHECK (max_orders > 0),
    CONSTRAINT uq_delivery_slot_zone_name UNIQUE (company_id, zone_id, name)
);
CREATE INDEX idx_delivery_slots_zone_active ON delivery_slots(company_id, zone_id, active);

ALTER TABLE customer_orders
    ADD COLUMN delivery_zone_id BIGINT REFERENCES delivery_zones(id),
    ADD COLUMN delivery_slot_id BIGINT REFERENCES delivery_slots(id),
    ADD COLUMN delivery_slot VARCHAR(80),
    ADD COLUMN delivery_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_customer_order_delivery_terms CHECK (
        (fulfillment_type = 'PICKUP' AND delivery_zone_id IS NULL AND delivery_slot_id IS NULL
            AND delivery_slot IS NULL AND delivery_fee = 0)
        OR
        (fulfillment_type = 'DELIVERY' AND delivery_fee >= 0 AND (
            (delivery_zone_id IS NULL AND delivery_slot_id IS NULL AND delivery_slot IS NULL AND delivery_fee = 0)
            OR (delivery_zone_id IS NOT NULL AND delivery_slot_id IS NOT NULL
                AND NULLIF(trim(delivery_slot), '') IS NOT NULL)
        ))
    );
CREATE INDEX idx_customer_orders_delivery_capacity
    ON customer_orders(company_id, delivery_zone_id, pickup_date, delivery_slot_id)
    WHERE fulfillment_type = 'DELIVERY' AND status <> 'CANCELLED';
