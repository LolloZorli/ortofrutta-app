ALTER TABLE products
    ADD COLUMN barcode VARCHAR(32);

CREATE UNIQUE INDEX uq_products_barcode
    ON products (lower(barcode))
    WHERE barcode IS NOT NULL;
