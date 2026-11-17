package it.tuonome.ortofrutta.sale;

import it.tuonome.ortofrutta.audit.AuditService;
import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.CurrentUser;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.common.PageResponse;
import it.tuonome.ortofrutta.lookup.LookupItem;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.lookup.LookupType;
import it.tuonome.ortofrutta.customer.CustomerRepository;
import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.product.ProductRepository;
import it.tuonome.ortofrutta.stock.MovementType;
import it.tuonome.ortofrutta.stock.StockMovement;
import it.tuonome.ortofrutta.stock.StockMovementRepository;
import it.tuonome.ortofrutta.stock.StockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cassa: una vendita con più prodotti in un'unica operazione atomica (o passa tutto o niente),
 * con metodo di pagamento. Si può stornare: la merce torna nei lotti e la vendita esce dai report.
 */
@Service
@Transactional(readOnly = true)
public class SaleService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final StockService stock;
    private final StockMovementRepository movements;
    private final LookupService lookups;
    private final AuditService audit;
    private final JdbcClient jdbc;
    private final CustomerRepository customers;

    public SaleService(SaleRepository sales, ProductRepository products, StockService stock,
                       StockMovementRepository movements, LookupService lookups,
                       AuditService audit, JdbcClient jdbc, CustomerRepository customers) {
        this.sales = sales;
        this.products = products;
        this.stock = stock;
        this.movements = movements;
        this.lookups = lookups;
        this.audit = audit;
        this.jdbc = jdbc;
        this.customers = customers;
    }

    /** canOverridePrice: se false, il prezzo di ogni riga è sempre quello di listino. */
    @Transactional
    public SaleDtos.Response checkout(SaleDtos.CheckoutRequest r, boolean canOverridePrice) {
        List<SaleItem> items = new ArrayList<>();
        for (SaleDtos.ItemRequest i : r.items()) {
            Product p = products.findById(i.productId())
                    .orElseThrow(() -> new NotFoundException("Prodotto", i.productId()));
            BigDecimal price = canOverridePrice && i.unitPrice() != null ? i.unitPrice() : p.getSalePrice();
            items.add(new SaleItem(p.getId(), i.quantity(), price));
        }
        if (r.customerId() != null && !customers.existsById(r.customerId())) {
            throw new NotFoundException("Cliente", r.customerId());
        }
        return create(items, r.paymentMethodId(), r.customerId());
    }

    /** Crea la vendita con prezzi già decisi (usato dalla cassa e dal ritiro delle prenotazioni). */
    @Transactional
    public SaleDtos.Response create(List<SaleItem> items, Long paymentMethodId) {
        return create(items, paymentMethodId, null);
    }

    @Transactional
    public SaleDtos.Response create(List<SaleItem> items, Long paymentMethodId, Long customerId) {
        LookupItem method = lookups.requireActive(LookupType.PAYMENT_METHOD, paymentMethodId, "Metodo di pagamento");
        Sale sale = sales.save(new Sale(CurrentUser.name(), method.id(), customerId));
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem item : items) {
            stock.sellProduct(item.productId(), item.quantity(), item.unitPrice(), sale.getId());
            total = total.add(item.quantity().multiply(item.unitPrice()));
        }
        sale.setTotal(total.setScale(2, RoundingMode.HALF_UP));
        return toResponse(sale, true);
    }

    @Transactional
    public SaleDtos.Response voidSale(Long id, String reason) {
        Sale sale = find(id);
        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new BusinessException("Questa vendita è già stata stornata");
        }
        for (StockMovement m : movements.findBySaleId(id)) {
            if (m.getType() == MovementType.SALE && !m.isVoided()) {
                m.getLot().restore(m.getQuantity());
                m.markVoided();
            }
        }
        sale.markVoided(CurrentUser.name(), reason.trim());
        audit.record("SALE_VOID", "SALE", id, "totale " + sale.getTotal() + ": " + reason.trim());
        return toResponse(sale, true);
    }

    /** Chi non può vedere gli incassi di tutti (onlyMine) può aprire solo le proprie vendite. */
    public SaleDtos.Response get(Long id, boolean onlyMine) {
        Sale sale = find(id);
        if (onlyMine && !CurrentUser.name().equals(sale.getCreatedBy())) {
            throw new NotFoundException("Vendita", id);
        }
        return toResponse(sale, true);
    }

    /** onlyMine: chi non può vedere gli incassi di tutti vede solo le proprie vendite. */
    public PageResponse<SaleDtos.Response> list(Pageable pageable, boolean onlyMine) {
        Page<Sale> page = onlyMine
                ? sales.findByCreatedByOrderByCreatedAtDescIdDesc(CurrentUser.name(), pageable)
                : sales.findAllByOrderByCreatedAtDescIdDesc(pageable);
        return PageResponse.of(page, s -> toResponse(s, false));
    }

    public List<SaleDtos.Response> customerHistory(Long customerId) {
        if (!customers.existsById(customerId)) throw new NotFoundException("Cliente", customerId);
        return sales.findAllByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, SaleStatus.COMPLETED)
                .stream().map(s -> toResponse(s, true)).toList();
    }

    // ---------------------------------- interni ----------------------------------

    private SaleDtos.Response toResponse(Sale s, boolean withLines) {
        return new SaleDtos.Response(s.getId(), s.getCreatedAt(), s.getCreatedBy(),
                lookups.name(LookupType.PAYMENT_METHOD, s.getPaymentMethodId()),
                s.getTotal(), s.getStatus(), s.getVoidedAt(), s.getVoidedBy(), s.getVoidReason(),
                withLines ? lines(s.getId()) : List.of(), s.getCustomerId(), customerName(s.getCustomerId()));
    }

    private String customerName(Long customerId) {
        if (customerId == null) return null;
        return jdbc.sql("select name from customers where id = :id").param("id", customerId)
                .query(String.class).optional().orElse(null);
    }

    /** Righe riassunte per prodotto e prezzo (una vendita su più lotti genera più movimenti). */
    private List<SaleDtos.Line> lines(Long saleId) {
        return jdbc.sql("""
                select p.name, u.short_label, m.unit_price, sum(m.quantity) as qty
                from stock_movements m
                join products p on p.id = m.product_id
                join units u on u.id = p.unit_id
                where m.sale_id = :id and m.type = 'SALE'
                group by p.name, u.short_label, m.unit_price
                order by p.name, m.unit_price
                """).param("id", saleId).query((rs, n) -> {
            BigDecimal qty = rs.getBigDecimal("qty");
            BigDecimal price = rs.getBigDecimal("unit_price");
            return new SaleDtos.Line(rs.getString("name"), rs.getString("short_label"), qty, price,
                    qty.multiply(price).setScale(2, RoundingMode.HALF_UP));
        }).list();
    }

    private Sale find(Long id) {
        return sales.findById(id).orElseThrow(() -> new NotFoundException("Vendita", id));
    }
}
