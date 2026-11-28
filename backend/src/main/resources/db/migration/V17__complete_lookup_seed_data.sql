-- Valori iniziali completi per le tipologiche operative.
-- ON CONFLICT evita duplicati nei negozi già esistenti.

INSERT INTO product_categories(company_id, code, name, sort_order, active)
SELECT c.id, v.code, v.name, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('FRUTTA', 'Frutta', 1),
    ('VERDURA', 'Verdura', 2),
    ('ERBE_AROMATICHE', 'Erbe aromatiche', 3),
    ('LEGUMI_SECCHI', 'Legumi secchi', 4),
    ('FRUTTA_SECCA', 'Frutta secca', 5),
    ('FUNGI', 'Funghi', 6),
    ('PRODOTTI_GASTRONOMICI', 'Prodotti gastronomici', 7),
    ('ORTOFRUTTA_CONFEZIONATA', 'Ortofrutta confezionata', 8),
    ('ALTRO', 'Altro', 99)
) AS v(code, name, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, active = TRUE;

INSERT INTO units(company_id, code, name, short_label, allow_decimals, sort_order, active)
SELECT c.id, v.code, v.name, v.short_label, v.allow_decimals, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('KG', 'Chilogrammo', 'kg', TRUE, 1),
    ('GRAMMI', 'Grammi', 'g', TRUE, 2),
    ('PIECE', 'Pezzo', 'pz', FALSE, 3),
    ('CRATE', 'Cassetta', 'cassetta', TRUE, 4),
    ('BUNCH', 'Mazzo', 'mazzo', FALSE, 5),
    ('PUNNET', 'Vaschetta', 'vaschetta', FALSE, 6),
    ('TRAY', 'Vassoio', 'vassoio', FALSE, 7),
    ('PACK', 'Confezione', 'conf.', FALSE, 8),
    ('BOX', 'Box', 'box', TRUE, 9),
    ('BUNCH_GRAPES', 'Grappolo', 'grappolo', FALSE, 10)
) AS v(code, name, short_label, allow_decimals, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, short_label = EXCLUDED.short_label,
    allow_decimals = EXCLUDED.allow_decimals, sort_order = EXCLUDED.sort_order, active = TRUE;

INSERT INTO vat_rates(company_id, code, name, rate, sort_order, active)
SELECT c.id, v.code, v.name, v.rate, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('IVA_0', 'IVA 0%', 0, 1),
    ('IVA_4', 'IVA 4%', 4, 2),
    ('IVA_5', 'IVA 5%', 5, 3),
    ('IVA_10', 'IVA 10%', 10, 4),
    ('IVA_22', 'IVA 22%', 22, 5)
) AS v(code, name, rate, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, rate = EXCLUDED.rate, sort_order = EXCLUDED.sort_order, active = TRUE;

INSERT INTO origins(company_id, code, name, sort_order, active)
SELECT c.id, v.code, v.name, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('ITALIA', 'Italia', 1),
    ('LAZIO', 'Lazio', 2),
    ('LOMBARDIA', 'Lombardia', 3),
    ('PIEMONTE', 'Piemonte', 4),
    ('VENETO', 'Veneto', 5),
    ('EMILIA_ROMAGNA', 'Emilia-Romagna', 6),
    ('TOSCANA', 'Toscana', 7),
    ('CAMPANIA', 'Campania', 8),
    ('PUGLIA', 'Puglia', 9),
    ('CALABRIA', 'Calabria', 10),
    ('SICILIA', 'Sicilia', 11),
    ('SARDEGNA', 'Sardegna', 12),
    ('ESTERO', 'Estero', 13),
    ('ECUADOR', 'Ecuador', 14),
    ('COSTA_RICA', 'Costa Rica', 15),
    ('SPAIN', 'Spagna', 16),
    ('GRECIA', 'Grecia', 17),
    ('TURCHIA', 'Turchia', 18),
    ('MAROCCO', 'Marocco', 19),
    ('ALTRI_PAESI', 'Altri paesi', 99)
) AS v(code, name, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, active = TRUE;

INSERT INTO waste_reasons(company_id, code, name, sort_order, active)
SELECT c.id, v.code, v.name, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('TROPPO_MATURE', 'Troppo mature', 1),
    ('SCADUTO', 'Scaduto', 2),
    ('DANNEGGIATO', 'Danneggiato', 3),
    ('APPASSITO', 'Appassito', 4),
    ('SLICE', 'Schiacciato', 5),
    ('CONSERVAZIONE', 'Conservazione non corretta', 6),
    ('RESO_FORNITORE', 'Reso al fornitore', 7),
    ('CONTROLLO_QUALITA', 'Controllo qualità', 8),
    ('ALTRO', 'Altro', 99)
) AS v(code, name, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, active = TRUE;

INSERT INTO payment_methods(company_id, code, name, sort_order, active)
SELECT c.id, v.code, v.name, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('CASH', 'Contanti', 1),
    ('CARD', 'Carta / Bancomat', 2),
    ('CONTACTLESS', 'Pagamento contactless', 3),
    ('BANK_TRANSFER', 'Bonifico bancario', 4),
    ('SATISPAY', 'Satispay', 5),
    ('VOUCHER', 'Buono / voucher', 6),
    ('OTHER', 'Altro', 99)
) AS v(code, name, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, active = TRUE;

INSERT INTO customer_types(company_id, code, name, sort_order, active)
SELECT c.id, v.code, v.name, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('PRIVATE', 'Privato', 1),
    ('RESTAURANT', 'Ristorante / locale', 2),
    ('CAFE', 'Bar / pasticceria', 3),
    ('BUSINESS', 'Azienda / ente', 4),
    ('HORECA', 'Impianto HORECA', 5),
    ('ASSOCIATION', 'Associazione', 6),
    ('SCHOOL', 'Scuola / comunità', 7),
    ('WHOLESALER', 'Dettaglio / rivenditore', 8),
    ('OTHER', 'Altro', 99)
) AS v(code, name, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, active = TRUE;

INSERT INTO supplier_types(company_id, code, name, sort_order, active)
SELECT c.id, v.code, v.name, v.sort_order, TRUE
FROM companies c CROSS JOIN (VALUES
    ('PRODUCER', 'Azienda agricola / produttore', 1),
    ('WHOLESALER', 'Grossista', 2),
    ('MARKET', 'Mercato ortofrutticolo', 3),
    ('COOPERATIVE', 'Cooperativa', 4),
    ('IMPORTER', 'Importatore', 5),
    ('DISTRIBUTOR', 'Distributore', 6),
    ('ORGANIC', 'Produttore biologico', 7),
    ('RETAILER', 'Dettaglio / rivenditore', 8),
    ('OTHER', 'Altro', 99)
) AS v(code, name, sort_order)
ON CONFLICT (company_id, code) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, active = TRUE;
