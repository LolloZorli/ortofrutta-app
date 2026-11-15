package it.tuonome.ortofrutta.supplierorder;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.audit.AuditService;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.common.PageResponse;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.product.ProductRepository;
import it.tuonome.ortofrutta.stock.StockService;
import it.tuonome.ortofrutta.supplier.Supplier;
import it.tuonome.ortofrutta.supplier.SupplierRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SupplierOrderService {

    private final SupplierOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final StockService stock;
    private final LookupService lookups;
    private final AuditService audit;

    public SupplierOrderService(SupplierOrderRepository orders, SupplierRepository suppliers,
                                ProductRepository products, StockService stock,
                                LookupService lookups, AuditService audit) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.products = products;
        this.stock = stock;
        this.lookups = lookups;
        this.audit = audit;
    }

    public PageResponse<SupplierOrderDtos.Response> list(Pageable pageable) {
        return PageResponse.of(orders.findAllByOrderByCreatedAtDescIdDesc(pageable),
                o -> SupplierOrderDtos.Response.from(o, lookups));
    }

    public SupplierOrderDtos.Response get(Long id) {
        return SupplierOrderDtos.Response.from(find(id), lookups);
    }

    @Transactional
    public SupplierOrderDtos.Response create(SupplierOrderDtos.Request r) {
        Supplier supplier = suppliers.findById(r.supplierId())
                .orElseThrow(() -> new NotFoundException("Fornitore", r.supplierId()));
        SupplierOrder order = new SupplierOrder(supplier, r.notes());
        for (SupplierOrderDtos.LineRequest l : r.lines()) {
            Product p = products.findById(l.productId())
                    .orElseThrow(() -> new NotFoundException("Prodotto", l.productId()));
            order.addLine(p, l.quantity(), l.unitCost() != null ? l.unitCost() : BigDecimal.ZERO);
        }
        return SupplierOrderDtos.Response.from(orders.save(order), lookups);
    }

    /** L'ordine è stato inviato al fornitore (es. via WhatsApp o telefono). */
    @Transactional
    public SupplierOrderDtos.Response send(Long id) {
        SupplierOrder o = find(id);
        if (o.getStatus() != SupplierOrderStatus.DRAFT) {
            throw new BusinessException("Solo un ordine in bozza può essere inviato");
        }
        o.markSent();
        audit.record("ORDER_SEND", "SUPPLIER_ORDER", id, o.getSupplier().getName());
        return SupplierOrderDtos.Response.from(o, lookups);
    }

    /**
     * Registra l'arrivo della merce: per ogni riga crea un lotto a magazzino.
     * Quantità, costo e scadenza possono essere corretti riga per riga rispetto all'ordine.
     */
    @Transactional
    public SupplierOrderDtos.Response receive(Long id, SupplierOrderDtos.ReceiveRequest request) {
        SupplierOrder o = find(id);
        if (o.getStatus() != SupplierOrderStatus.DRAFT && o.getStatus() != SupplierOrderStatus.SENT) {
            throw new BusinessException("Questo ordine non può essere ricevuto");
        }
        Map<Long, SupplierOrderDtos.LineReceipt> overrides =
                request == null || request.lines() == null ? Map.of()
                        : request.lines().stream()
                        .collect(Collectors.toMap(SupplierOrderDtos.LineReceipt::lineId, Function.identity(), (a, b) -> b));

        for (SupplierOrderLine line : o.getLines()) {
            SupplierOrderDtos.LineReceipt ov = overrides.get(line.getId());
            BigDecimal qty = ov != null && ov.quantity() != null ? ov.quantity() : line.getQuantity();
            BigDecimal cost = ov != null && ov.unitCost() != null ? ov.unitCost() : line.getUnitCost();
            if (qty.signum() > 0) {
                stock.receiveLot(line.getProduct().getId(), o.getSupplier().getId(), qty, cost,
                        null, ov != null ? ov.expiresAt() : null);
            }
        }
        o.markReceived();
        audit.record("ORDER_RECEIVE", "SUPPLIER_ORDER", id, o.getSupplier().getName());
        return SupplierOrderDtos.Response.from(o, lookups);
    }

    @Transactional
    public SupplierOrderDtos.Response cancel(Long id) {
        SupplierOrder o = find(id);
        if (o.getStatus() == SupplierOrderStatus.RECEIVED || o.getStatus() == SupplierOrderStatus.CANCELLED) {
            throw new BusinessException("Questo ordine non può essere annullato");
        }
        o.markCancelled();
        return SupplierOrderDtos.Response.from(o, lookups);
    }

    @Transactional
    public void delete(Long id) {
        SupplierOrder o = find(id);
        if (o.getStatus() != SupplierOrderStatus.DRAFT && o.getStatus() != SupplierOrderStatus.CANCELLED) {
            throw new BusinessException("Si possono eliminare solo ordini in bozza o annullati");
        }
        orders.delete(o);
    }

    private SupplierOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Ordine fornitore", id));
    }
}
