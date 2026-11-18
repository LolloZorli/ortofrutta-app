package it.tuonome.ortofrutta.auth;

/**
 * I permessi: elenco fisso nel codice (ogni permesso corrisponde a una funzione dell'applicazione).
 * I ruoli sono dati nel database: l'amministratore li crea e sceglie quali permessi dare.
 * I permessi marcati "solo amministratore" non si possono assegnare ad altri ruoli.
 */
public enum Permission {
    PRODUCTS_READ("Vedere il listino prodotti", "Prodotti", false),
    CATALOG_READ("Vedere il catalogo pubblico per effettuare ordini", "Clienti online", false),
    CUSTOMER_ORDER_CREATE("Creare ordini cliente", "Clienti online", false),
    CUSTOMER_ORDER_READ("Vedere i propri ordini cliente", "Clienti online", false),
    PRODUCTS_WRITE("Aggiungere e modificare prodotti e prezzi di listino", "Prodotti", false),

    SALES_CREATE("Registrare vendite in cassa", "Cassa", false),
    SALES_PRICE_OVERRIDE("Cambiare il prezzo in cassa (sconti)", "Cassa", false),
    SALES_VOID("Stornare le vendite", "Cassa", false),

    STOCK_READ("Vedere giacenze, lotti e movimenti", "Magazzino", false),
    STOCK_RECEIVE("Caricare merce a magazzino", "Magazzino", false),
    WASTE_CREATE("Registrare scarti", "Magazzino", false),

    COSTS_VIEW("Vedere costi di acquisto e margini", "Dati economici", false),
    REPORTS_VIEW("Vedere report, incassi e corrispettivi", "Dati economici", false),

    SUPPLIERS_READ("Consultare i fornitori", "Fornitori e ordini", false),
    SUPPLIERS_MANAGE("Gestire i fornitori", "Fornitori e ordini", false),
    SUPPLIER_ORDER_CREATE("Creare ordini ai fornitori", "Fornitori e ordini", false),
    SUPPLIER_ORDERS_MANAGE("Gestire gli ordini ai fornitori", "Fornitori e ordini", false),

    CUSTOMERS_MANAGE("Gestire i clienti", "Clienti", false),
    CUSTOMERS_DELETE("Eliminare i clienti", "Clienti", false),
    RESERVATIONS_MANAGE("Gestire le prenotazioni", "Clienti", false),

    AUDIT_VIEW("Consultare il registro delle attività", "Amministrazione", false),
    USERS_MANAGE("Gestire gli utenti", "Amministrazione", true),
    USERS_READ("Vedere commessi e richieste di accesso", "Amministrazione", false),
    USERS_APPROVE("Approvare le richieste dei commessi", "Amministrazione", false),
    ROLES_MANAGE("Gestire ruoli e permessi", "Amministrazione", true),
    LOOKUPS_MANAGE("Gestire le tipologiche (categorie, unità, IVA...)", "Amministrazione", true);

    private final String label;
    private final String group;
    private final boolean adminOnly;

    Permission(String label, String group, boolean adminOnly) {
        this.label = label;
        this.group = group;
        this.adminOnly = adminOnly;
    }

    public String label() { return label; }
    public String group() { return group; }
    public boolean adminOnly() { return adminOnly; }
}
