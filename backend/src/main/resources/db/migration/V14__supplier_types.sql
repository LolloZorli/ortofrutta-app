CREATE TABLE supplier_types (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    code VARCHAR(60) NOT NULL,
    name VARCHAR(80) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_supplier_types_company_code ON supplier_types(company_id, code);
CREATE UNIQUE INDEX uq_supplier_types_company_name ON supplier_types(company_id, lower(name));

INSERT INTO supplier_types (company_id, code, name, sort_order)
SELECT c.id, t.code, t.name, t.sort_order
FROM companies c
CROSS JOIN (VALUES
    ('PRODUCER', 'Azienda agricola / produttore', 1),
    ('WHOLESALER', 'Grossista', 2),
    ('MARKET', 'Mercato ortofrutticolo', 3),
    ('COOPERATIVE', 'Cooperativa', 4),
    ('IMPORTER', 'Importatore', 5),
    ('OTHER', 'Altro', 6)
) AS t(code, name, sort_order);

ALTER TABLE suppliers ADD COLUMN supplier_type_id BIGINT REFERENCES supplier_types(id);
UPDATE suppliers s
SET supplier_type_id = st.id
FROM supplier_types st
WHERE st.company_id = s.company_id AND st.code = 'OTHER';
