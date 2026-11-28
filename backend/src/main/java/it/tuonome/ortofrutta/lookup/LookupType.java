package it.tuonome.ortofrutta.lookup;

/**
 * Le tipologiche: tabelle di decodifica con dati "statici" che solo l'amministratore può aggiungere o modificare.
 * Ogni valore ha una tabella dedicata (con chiavi esterne dai dati che lo usano).
 * Gli stati dei flussi (bozza/inviato/ricevuto...) NON sono tipologiche: la logica del codice dipende da loro.
 */
public enum LookupType {
    CATEGORY("product_categories", "Categorie prodotto", false, false),
    UNIT("units", "Unità di misura", true, false),
    VAT_RATE("vat_rates", "Aliquote IVA", false, true),
    ORIGIN("origins", "Origini", false, false),
    WASTE_REASON("waste_reasons", "Motivi di scarto", false, false),
    PAYMENT_METHOD("payment_methods", "Metodi di pagamento", false, false),
    CUSTOMER_TYPE("customer_types", "Tipi di cliente", false, false),
    SUPPLIER_TYPE("supplier_types", "Tipi di fornitore", false, false),
    REGION("regions", "Regioni", false, false),
    PROVINCE("provinces", "Province", false, false),
    CITY("cities", "Città", false, false),
    STREET_TYPE("street_types", "Tipi di via", false, false);

    private final String table;
    private final String label;
    private final boolean unitAttributes;
    private final boolean rate;

    LookupType(String table, String label, boolean unitAttributes, boolean rate) {
        this.table = table;
        this.label = label;
        this.unitAttributes = unitAttributes;
        this.rate = rate;
    }

    /** Nome della tabella: valori fissi del codice, mai input dell'utente (quindi sicuri nelle query). */
    public String table() { return table; }
    public String label() { return label; }
    /** Le unità di misura hanno etichetta breve e il flag "ammette decimali". */
    public boolean hasUnitAttributes() { return unitAttributes; }
    /** Le aliquote IVA hanno la percentuale. */
    public boolean hasRate() { return rate; }
}
