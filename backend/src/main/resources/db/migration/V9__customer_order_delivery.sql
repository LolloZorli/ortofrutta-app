ALTER TABLE customer_orders
    ADD COLUMN fulfillment_type VARCHAR(10) NOT NULL DEFAULT 'PICKUP',
    ADD COLUMN delivery_address VARCHAR(250),
    ADD COLUMN delivery_city VARCHAR(120),
    ADD COLUMN delivery_postal_code VARCHAR(20),
    ADD COLUMN delivery_phone VARCHAR(30),
    ADD COLUMN delivery_notes VARCHAR(500);

ALTER TABLE customer_orders
    ADD CONSTRAINT chk_customer_orders_fulfillment_type
        CHECK (fulfillment_type IN ('PICKUP', 'DELIVERY')),
    ADD CONSTRAINT chk_customer_orders_delivery_details
        CHECK (fulfillment_type <> 'DELIVERY' OR (
            NULLIF(trim(delivery_address), '') IS NOT NULL
            AND NULLIF(trim(delivery_city), '') IS NOT NULL
            AND NULLIF(trim(delivery_postal_code), '') IS NOT NULL
            AND NULLIF(trim(delivery_phone), '') IS NOT NULL
        ));

ALTER TABLE customer_orders
    DROP CONSTRAINT customer_orders_status_check,
    ADD CONSTRAINT customer_orders_status_check
        CHECK (status IN ('NEW', 'READY', 'PICKED_UP', 'DELIVERED', 'CANCELLED'));
