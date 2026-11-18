package it.tuonome.ortofrutta.demo;

import it.tuonome.ortofrutta.auth.AppRole;
import it.tuonome.ortofrutta.auth.AppUser;
import it.tuonome.ortofrutta.auth.RoleRepository;
import it.tuonome.ortofrutta.auth.UserRepository;
import it.tuonome.ortofrutta.company.CompanyContext;
import it.tuonome.ortofrutta.company.CompanyMembership;
import it.tuonome.ortofrutta.company.CompanyMembershipRepository;
import it.tuonome.ortofrutta.customer.CustomerDtos;
import it.tuonome.ortofrutta.customer.CustomerService;
import it.tuonome.ortofrutta.customerorder.CustomerOrderDtos;
import it.tuonome.ortofrutta.customerorder.CustomerOrderService;
import it.tuonome.ortofrutta.lookup.LookupDtos;
import it.tuonome.ortofrutta.lookup.LookupItem;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import it.tuonome.ortofrutta.product.ProductDtos;
import it.tuonome.ortofrutta.product.ProductRepository;
import it.tuonome.ortofrutta.product.ProductService;
import it.tuonome.ortofrutta.sale.SaleItem;
import it.tuonome.ortofrutta.sale.SaleService;
import it.tuonome.ortofrutta.stock.StockService;
import it.tuonome.ortofrutta.supplier.SupplierDtos;
import it.tuonome.ortofrutta.supplier.SupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Dati di prova per provare l'app senza inserire tutto a mano. Attivo solo col profilo "demo". */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final UserRepository users;
    private final CompanyMembershipRepository memberships;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final ProductRepository productRepository;
    private final ProductService products;
    private final SupplierService suppliers;
    private final CustomerService customers;
    private final StockService stock;
    private final SaleService sales;
    private final CustomerOrderService customerOrders;
    private final LookupService lookups;

    public DemoDataLoader(UserRepository users, CompanyMembershipRepository memberships, RoleRepository roles, PasswordEncoder encoder,
                          ProductRepository productRepository, ProductService products,
                          SupplierService suppliers, CustomerService customers,
                          StockService stock, SaleService sales, CustomerOrderService customerOrders,
                          LookupService lookups) {
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.encoder = encoder;
        this.productRepository = productRepository;
        this.products = products;
        this.suppliers = suppliers;
        this.customers = customers;
        this.stock = stock;
        this.sales = sales;
        this.customerOrders = customerOrders;
        this.lookups = lookups;
    }

    @Override
    public void run(ApplicationArguments args) {
        demoUser("commesso", "commesso123", "STAFF");
        demoUser("responsabile", "responsabile123", "MANAGER");
        demoUser("magazziniere", "magazziniere123", "WAREHOUSE");

        if (productRepository.count() > 0) {
            return;
        }

        long frutta = id(LookupType.CATEGORY, "FRUTTA");
        long verdura = id(LookupType.CATEGORY, "VERDURA");
        long kg = id(LookupType.UNIT, "KG");
        long pz = id(LookupType.UNIT, "PIECE");
        long cassetta = id(LookupType.UNIT, "CRATE");
        long iva4 = id(LookupType.VAT_RATE, "IVA_4");
        long cash = id(LookupType.PAYMENT_METHOD, "CASH");
        long card = id(LookupType.PAYMENT_METHOD, "CARD");

        var mercato = suppliers.create(new SupplierDtos.Request("Mercato Ortofrutticolo", "333 1234567", null, "Banco 14, ordini entro le 18"));
        var azienda = suppliers.create(new SupplierDtos.Request("Azienda Agricola Rossi", "347 7654321", "rossi@example.com", null));

        var mele = product("Mele Golden", frutta, kg, "2.20", "10", origin("Trentino"), iva4);
        var banane = product("Banane", frutta, kg, "1.90", "8", origin("Ecuador"), iva4);
        var arance = product("Arance", frutta, kg, "1.80", "10", origin("Sicilia"), iva4);
        var pomodori = product("Pomodori ramati", verdura, kg, "3.50", "6", origin("Sicilia"), iva4);
        var zucchine = product("Zucchine", verdura, kg, "2.60", "5", origin("Lazio"), iva4);
        var insalata = product("Insalata gentile", verdura, pz, "1.20", "10", origin("Lazio"), iva4);
        var patate = product("Patate", verdura, kg, "1.40", "20", origin("Italia"), iva4);
        var limoni = product("Limoni", frutta, kg, "2.90", "4", origin("Sicilia"), iva4);
        var fragole = product("Fragole", frutta, cassetta, "4.50", "5", origin("Campania"), iva4);

        LocalDate today = LocalDate.now();
        stock.receiveLot(mele.id(), mercato.id(), bd("40"), bd("1.30"), today.minusDays(3), today.plusDays(10));
        stock.receiveLot(banane.id(), mercato.id(), bd("25"), bd("1.05"), today.minusDays(4), today.plusDays(2));
        stock.receiveLot(arance.id(), azienda.id(), bd("30"), bd("1.00"), today.minusDays(2), today.plusDays(8));
        stock.receiveLot(pomodori.id(), azienda.id(), bd("12"), bd("2.00"), today.minusDays(2), today.plusDays(3));
        stock.receiveLot(zucchine.id(), azienda.id(), bd("8"), bd("1.50"), today.minusDays(3), today.plusDays(1));
        stock.receiveLot(insalata.id(), azienda.id(), bd("25"), bd("0.60"), today.minusDays(2), today.plusDays(2));
        stock.receiveLot(patate.id(), mercato.id(), bd("60"), bd("0.70"), today.minusDays(6), today.plusDays(20));
        stock.receiveLot(limoni.id(), mercato.id(), bd("3"), bd("1.70"), today.minusDays(5), today.plusDays(9));
        stock.receiveLot(fragole.id(), mercato.id(), bd("10"), bd("2.80"), today.minusDays(1), today.plusDays(2));

        // Vendite di cassa con più prodotti e metodi di pagamento diversi
        sales.create(List.of(item(mele, "2.5"), item(arance, "3"), item(insalata, "2")), cash);
        sales.create(List.of(item(banane, "4"), item(pomodori, "1.5")), card);
        sales.create(List.of(item(patate, "10"), item(zucchine, "2")), cash);
        sales.create(List.of(item(mele, "10"), item(arance, "11"), item(pomodori, "6"), item(insalata, "10")), card);
        sales.create(List.of(item(banane, "5"), item(zucchine, "2.5"), item(patate, "8")), cash);

        long troppoMature = id(LookupType.WASTE_REASON, "TROPPO_MATURE");
        long danneggiato = id(LookupType.WASTE_REASON, "DANNEGGIATO");
        long appassito = id(LookupType.WASTE_REASON, "APPASSITO");
        stock.discardProduct(banane.id(), bd("2.5"), troppoMature, "Troppo mature");
        stock.discardProduct(zucchine.id(), bd("1"), danneggiato, "Danneggiato");
        stock.discardProduct(insalata.id(), bd("3"), appassito, "Appassito");

        var privato = id(LookupType.CUSTOMER_TYPE, "PRIVATE");
        var ristorante = id(LookupType.CUSTOMER_TYPE, "RESTAURANT");
        var anna = customers.create(new CustomerDtos.Request("Anna Bianchi", "320 1112233", "Ritira sempre il sabato", privato));
        customers.create(new CustomerDtos.Request("Ristorante Da Gino", "06 5551234", "Consegna al mattino", ristorante));
        customerOrders.create(new CustomerOrderDtos.Request(anna.id(), today.plusDays(1), "Cassetta del sabato",
                List.of(new CustomerOrderDtos.LineRequest(mele.id(), bd("2"), null),
                        new CustomerOrderDtos.LineRequest(arance.id(), bd("3"), null),
                        new CustomerOrderDtos.LineRequest(insalata.id(), bd("2"), null))), true);

        log.info("Dati demo caricati");
    }

    private void demoUser(String username, String password, String roleCode) {
        if (users.existsByUsername(username)) {
            return;
        }
        AppRole role = roles.findByCode(roleCode).orElseThrow();
        AppUser user = users.save(new AppUser(username, encoder.encode(password), role, false));
        memberships.save(new CompanyMembership(CompanyContext.LEGACY_COMPANY_ID, user, role, null));
    }

    private long id(LookupType type, String code) {
        return lookups.list(type, true).stream()
                .filter(i -> code.equals(i.code())).findFirst().orElseThrow().id();
    }

    /** Origine esistente con questo nome, oppure nuova. */
    private long origin(String name) {
        return lookups.list(LookupType.ORIGIN, true).stream()
                .filter(i -> name.equalsIgnoreCase(i.name())).map(LookupItem::id).findFirst()
                .orElseGet(() -> lookups.create(LookupType.ORIGIN,
                        new LookupDtos.Request(name, null, null, null, null, null)).id());
    }

    private ProductDtos.Response product(String name, long category, long unit, String price,
                                         String minStock, long origin, long vat) {
        return products.create(new ProductDtos.Request(name, category, unit, bd(price), bd(minStock),
                origin, vat, true));
    }

    private SaleItem item(ProductDtos.Response p, String quantity) {
        return new SaleItem(p.id(), bd(quantity), p.salePrice());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
