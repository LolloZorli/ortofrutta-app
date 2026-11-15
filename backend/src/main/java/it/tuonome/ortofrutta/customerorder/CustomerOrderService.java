package it.tuonome.ortofrutta.customerorder;

import it.tuonome.ortofrutta.common.BusinessException;
import it.tuonome.ortofrutta.common.NotFoundException;
import it.tuonome.ortofrutta.customer.Customer;
import it.tuonome.ortofrutta.customer.CustomerRepository;
import it.tuonome.ortofrutta.product.Product;
import it.tuonome.ortofrutta.product.ProductRepository;
import it.tuonome.ortofrutta.lookup.LookupService;
import it.tuonome.ortofrutta.sale.SaleItem;
import it.tuonome.ortofrutta.sale.SaleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prenotazioni dei clienti: NEW → READY → PICKED_UP.
 * Al ritiro la merce viene venduta (scalata dai lotti in FIFO): se manca, il ritiro viene rifiutato.
 */
@Service
@Transactional(readOnly = true)
public class CustomerOrderService {

    private static final Set<CustomerOrderStatus> OPEN =
            Set.of(CustomerOrderStatus.NEW, CustomerOrderStatus.READY);

    private final CustomerOrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final SaleService sales;
    private final LookupService lookups;

    public CustomerOrderService(CustomerOrderRepository orders, CustomerRepository customers,
                                ProductRepository products, SaleService sales, LookupService lookups) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.sales = sales;
        this.lookups = lookups;
    }

    /** Aperte: tutte. Storico: le ultime 100. */
    public List<CustomerOrderDtos.Response> list(boolean onlyOpen) {
        List<CustomerOrder> list = onlyOpen
                ? orders.findByStatusInOrderByPickupDateAscIdAsc(OPEN)
                : orders.findAllByOrderByPickupDateDescIdDesc(PageRequest.of(0, 100));
        return list.stream().map(o -> CustomerOrderDtos.Response.from(o, lookups)).toList();
    }

    @Transactional
    public CustomerOrderDtos.Response create(CustomerOrderDtos.Request r, boolean canOverridePrice) {
        Customer customer = customers.findById(r.customerId())
                .orElseThrow(() -> new NotFoundException("Cliente", r.customerId()));
        CustomerOrder order = new CustomerOrder(customer, r.pickupDate(), r.notes());
        for (CustomerOrderDtos.LineRequest l : r.lines()) {
            Product p = products.findById(l.productId())
                    .orElseThrow(() -> new NotFoundException("Prodotto", l.productId()));
            order.addLine(p, l.quantity(),
                    canOverridePrice && l.unitPrice() != null ? l.unitPrice() : p.getSalePrice());
        }
        return CustomerOrderDtos.Response.from(orders.save(order), lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response markReady(Long id) {
        CustomerOrder o = find(id);
        if (o.getStatus() != CustomerOrderStatus.NEW) {
            throw new BusinessException("Solo una prenotazione nuova può essere segnata come pronta");
        }
        o.setStatus(CustomerOrderStatus.READY);
        return CustomerOrderDtos.Response.from(o, lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response pickUp(Long id, Long paymentMethodId) {
        CustomerOrder o = find(id);
        if (!OPEN.contains(o.getStatus())) {
            throw new BusinessException("Questa prenotazione è già chiusa");
        }
        // Il ritiro diventa una vendita di cassa (con metodo di pagamento): compare nei corrispettivi e si può stornare
        List<SaleItem> items = new ArrayList<>();
        for (CustomerOrderLine line : o.getLines()) {
            items.add(new SaleItem(line.getProduct().getId(), line.getQuantity(), line.getUnitPrice()));
        }
        sales.create(items, paymentMethodId);
        o.setStatus(CustomerOrderStatus.PICKED_UP);
        return CustomerOrderDtos.Response.from(o, lookups);
    }

    @Transactional
    public CustomerOrderDtos.Response cancel(Long id) {
        CustomerOrder o = find(id);
        if (!OPEN.contains(o.getStatus())) {
            throw new BusinessException("Questa prenotazione è già chiusa");
        }
        o.setStatus(CustomerOrderStatus.CANCELLED);
        return CustomerOrderDtos.Response.from(o, lookups);
    }

    private CustomerOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Prenotazione", id));
    }
}
